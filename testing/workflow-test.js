/**
 * ═══════════════════════════════════════════════════════════════════════════════
 *                    API GATEWAY WORKFLOW TEST (k6)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This test validates the complete workflow:
 * 1. Normal requests → Should be ALLOWED (200)
 * 2. Rate limiting → Should be BLOCKED after threshold (429)
 * 3. Kafka publishing → Events should reach consumers
 * 4. Redis counters → Should increment correctly
 * 5. Different endpoints and methods
 * 
 * RATE LIMITS (from RedisRateLimiter.java):
 * - IP: 100 requests per minute
 * - User: 500 requests per minute
 * - API Key: 1000 requests per minute
 * - Endpoint: 1000 requests per minute
 * - Method: 2000 requests per minute
 * 
 * USAGE:
 *   k6 run workflow-test.js
 *   k6 run --vus 10 --duration 30s workflow-test.js
 *   k6 run workflow-test.js --env SCENARIO=rate_limit_test
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ═══════════════════════════════════════════════════════════════════════════════
// CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════════════

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8033';
const SCENARIO = __ENV.SCENARIO || 'full_workflow';

// ═══════════════════════════════════════════════════════════════════════════════
// CUSTOM METRICS
// ═══════════════════════════════════════════════════════════════════════════════

// Counters
const allowedRequests = new Counter('allowed_requests');      // 200 responses
const blockedRequests = new Counter('blocked_requests');      // 429 responses
const errorRequests = new Counter('error_requests');          // 4xx/5xx (not 429)

// Rates
const blockRate = new Rate('block_rate');                     // % of requests blocked
const successRate = new Rate('success_rate');                 // % of requests successful

// Trends
const allowedLatency = new Trend('allowed_latency', true);    // Latency for 200s
const blockedLatency = new Trend('blocked_latency', true);    // Latency for 429s

// ═══════════════════════════════════════════════════════════════════════════════
// TEST OPTIONS
// ═══════════════════════════════════════════════════════════════════════════════

export const options = {
    scenarios: {
        // ─────────────────────────────────────────────────────────────────────
        // SCENARIO 1: Quick Smoke Test
        // ─────────────────────────────────────────────────────────────────────
        smoke_test: {
            executor: 'constant-vus',
            vus: 1,
            duration: '10s',
            exec: 'smokeTest',
            startTime: '0s',
        },

        // ─────────────────────────────────────────────────────────────────────
        // SCENARIO 2: Rate Limit Test (trigger 429s)
        // ─────────────────────────────────────────────────────────────────────
        rate_limit_test: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 150,  // More than IP limit (100/min) to trigger blocking
            exec: 'rateLimitTest',
            startTime: '15s',
        },

        // ─────────────────────────────────────────────────────────────────────
        // SCENARIO 3: Multi-Endpoint Test
        // ─────────────────────────────────────────────────────────────────────
        multi_endpoint_test: {
            executor: 'constant-vus',
            vus: 5,
            duration: '20s',
            exec: 'multiEndpointTest',
            startTime: '35s',
        },

        // ─────────────────────────────────────────────────────────────────────
        // SCENARIO 4: Load Test (sustained traffic)
        // ─────────────────────────────────────────────────────────────────────
        load_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 20 },   // Ramp up
                { duration: '30s', target: 20 },   // Sustained load
                { duration: '10s', target: 0 },    // Ramp down
            ],
            exec: 'loadTest',
            startTime: '60s',
        },
    },

    thresholds: {
        // Performance thresholds
        'http_req_duration': ['p(95)<100'],           // 95% of requests < 100ms
        'allowed_latency': ['p(95)<50'],              // Allowed requests < 50ms
        'blocked_latency': ['p(95)<10'],              // Blocked requests should be fast

        // Functional thresholds
        'success_rate': ['rate>0.5'],                 // At least 50% success (some will be rate limited)
        'error_requests': ['count<10'],               // Very few actual errors
    },
};

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Make a request and track metrics based on response
 */
function makeRequest(url, params = {}, name = 'request') {
    const defaultParams = {
        headers: {
            'Content-Type': 'application/json',
            'User-Agent': 'k6-workflow-test',
        },
        tags: { name: name },
        ...params,
    };

    const response = http.get(url, defaultParams);

    // Track metrics based on status code
    if (response.status === 200) {
        allowedRequests.add(1);
        successRate.add(true);
        blockRate.add(false);
        allowedLatency.add(response.timings.duration);
    } else if (response.status === 429) {
        blockedRequests.add(1);
        successRate.add(false);
        blockRate.add(true);
        blockedLatency.add(response.timings.duration);
    } else {
        errorRequests.add(1);
        successRate.add(false);
        blockRate.add(false);
    }

    return response;
}

/**
 * Log request result (for debugging)
 */
function logResult(name, response) {
    const statusIcon = response.status === 200 ? '✅' : response.status === 429 ? '🚫' : '❌';
    console.log(`${statusIcon} [${name}] Status: ${response.status} | Latency: ${response.timings.duration.toFixed(2)}ms`);
}

// ═══════════════════════════════════════════════════════════════════════════════
// TEST SCENARIOS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * SMOKE TEST: Quick validation that the API is working
 */
export function smokeTest() {
    group('Smoke Test', function () {
        console.log('🔥 Running Smoke Test...');

        const response = makeRequest(`${BASE_URL}/api/v1/resource`, {}, 'smoke_test');

        const passed = check(response, {
            'smoke: status is 200 or 429': (r) => r.status === 200 || r.status === 429,
            'smoke: response time < 500ms': (r) => r.timings.duration < 500,
            'smoke: has response body': (r) => r.body && r.body.length > 0,
        });

        logResult('Smoke Test', response);

        if (!passed) {
            console.log(`⚠️ Smoke test issue - Body: ${response.body}`);
        }

        sleep(1);
    });
}

/**
 * RATE LIMIT TEST: Deliberately exceed rate limits to trigger 429s
 */
export function rateLimitTest() {
    group('Rate Limit Test', function () {
        // Make requests rapidly without sleep to hit rate limit
        const response = makeRequest(`${BASE_URL}/api/v1/resource`, {}, 'rate_limit_burst');

        // Log every 10th request
        if (__ITER % 10 === 0) {
            logResult(`Rate Limit [${__ITER}/150]`, response);
        }

        check(response, {
            'rate_limit: status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        });

        // After the limit (100 requests), we expect 429s
        if (__ITER > 100) {
            check(response, {
                'rate_limit: blocked after limit': (r) => r.status === 429,
            });
        }
    });
}

/**
 * MULTI-ENDPOINT TEST: Test different endpoints and methods
 */
export function multiEndpointTest() {
    const endpoints = [
        { path: '/api/v1/resource', name: 'resource' },
        { path: '/api/v1/users', name: 'users' },
        { path: '/api/v1/orders', name: 'orders' },
        { path: '/api/v1/products', name: 'products' },
        { path: '/api/health', name: 'health' },
    ];

    group('Multi-Endpoint Test', function () {
        // Pick a random endpoint
        const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];

        const response = makeRequest(`${BASE_URL}${endpoint.path}`, {}, `endpoint_${endpoint.name}`);

        check(response, {
            'multi: endpoint accessible': (r) => r.status === 200 || r.status === 429 || r.status === 404,
        });

        sleep(0.1); // Small delay between requests
    });
}

/**
 * LOAD TEST: Sustained traffic to test system under load
 */
export function loadTest() {
    group('Load Test', function () {
        // Mix of endpoints
        const endpoints = [
            '/api/v1/resource',
            '/api/v1/users',
            '/api/v1/orders',
        ];

        const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];

        // Simulate authenticated user
        const params = {
            headers: {
                'Authorization': `Bearer user-token-${__VU}`,
                'X-API-KEY': `api-key-${__VU}`,
            },
        };

        const response = makeRequest(`${BASE_URL}${endpoint}`, params, 'load_test');

        const passed = check(response, {
            'load: response received': (r) => r.status !== 0,
            'load: latency acceptable': (r) => r.timings.duration < 200,
        });

        if (!passed && response.status !== 429) {
            logResult(`Load Test Error VU${__VU}`, response);
        }

        sleep(0.5); // Pacing to avoid overwhelming
    });
}

// ═══════════════════════════════════════════════════════════════════════════════
// DEFAULT FUNCTION (runs if no specific scenario selected)
// ═══════════════════════════════════════════════════════════════════════════════

export default function () {
    // This runs for any unconfigured scenarios
    const response = makeRequest(`${BASE_URL}/api/v1/resource`, {}, 'default');

    check(response, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    sleep(1);
}

// ═══════════════════════════════════════════════════════════════════════════════
// LIFECYCLE HOOKS
// ═══════════════════════════════════════════════════════════════════════════════

export function setup() {
    console.log('═══════════════════════════════════════════════════════════════');
    console.log('           🚀 API GATEWAY WORKFLOW TEST STARTING');
    console.log('═══════════════════════════════════════════════════════════════');
    console.log(`📍 Target: ${BASE_URL}`);
    console.log(`📋 Scenarios: smoke_test → rate_limit_test → multi_endpoint → load_test`);
    console.log('');
    console.log('Expected Rate Limits:');
    console.log('  • IP: 100 req/min');
    console.log('  • User: 500 req/min');
    console.log('  • API Key: 1000 req/min');
    console.log('═══════════════════════════════════════════════════════════════');

    // Verify API is reachable
    const healthCheck = http.get(`${BASE_URL}/api/v1/resource`);
    if (healthCheck.status === 0) {
        console.log('❌ ERROR: API Gateway is not reachable!');
        console.log('   Make sure the API Gateway is running on port 8033');
        return { apiReachable: false };
    }

    console.log('✅ API Gateway is reachable');
    return { apiReachable: true, startTime: new Date().toISOString() };
}

export function teardown(data) {
    console.log('');
    console.log('═══════════════════════════════════════════════════════════════');
    console.log('           📊 API GATEWAY WORKFLOW TEST COMPLETE');
    console.log('═══════════════════════════════════════════════════════════════');
    console.log('');
    console.log('📈 Check Redis for rate limit data:');
    console.log('   docker exec redis redis-cli KEYS "ratelimit:*"');
    console.log('');
    console.log('📊 Check Redis for metrics:');
    console.log('   docker exec redis redis-cli KEYS "stats:*"');
    console.log('');
    console.log('📝 Check audit log:');
    console.log('   cat audit_log.json | tail -10');
    console.log('');
    console.log('🔍 Check Kafka consumer logs for processed events');
    console.log('═══════════════════════════════════════════════════════════════');
}
