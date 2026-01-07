/**
 * ═══════════════════════════════════════════════════════════════════════════════
 *                    QUICK RATE LIMIT TEST (k6)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * A simple, focused test to verify rate limiting works correctly.
 * 
 * WHAT THIS TESTS:
 * 1. First ~100 requests should be ALLOWED (200)
 * 2. Requests after that should be BLOCKED (429)
 * 
 * USAGE:
 *   k6 run quick-rate-limit-test.js
 * 
 * EXPECTED RESULTS:
 *   - First 100 requests: 200 OK
 *   - Requests 101+: 429 Too Many Requests
 *   - Total blocked should be ~50 (out of 150)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8033';

// Metrics
const allowed = new Counter('allowed_count');
const blocked = new Counter('blocked_count');
const errors = new Counter('error_count');
const latency = new Trend('request_latency', true);

// Test options - single VU making 150 rapid requests
export const options = {
    vus: 100,
    iterations: 1000,
    thresholds: {
        'allowed_count': ['count>=90'],     // At least 90 allowed (some margin)
        'blocked_count': ['count>=40'],     // At least 40 blocked (some margin)
        'error_count': ['count<5'],         // Almost no real errors
    },
};

export default function () {
    const response = http.get(`${BASE_URL}/api/v1/resource`, {
        headers: {
            'Content-Type': 'application/json',
        },
        timeout: '10s',
    });

    latency.add(response.timings.duration);

    // Track by status
    if (response.status === 200) {
        allowed.add(1);
    } else if (response.status === 429) {
        blocked.add(1);
    } else {
        errors.add(1);
    }

    // Progress logging every 25 requests
    if (__ITER % 25 === 0 || __ITER < 5 || __ITER > 145) {
        const icon = response.status === 200 ? '✅' : response.status === 429 ? '🚫' : '❌';
        console.log(`${icon} Request ${__ITER + 1}/150 | Status: ${response.status} | Latency: ${response.timings.duration.toFixed(1)}ms`);
    }

    // Expectations
    if (__ITER < 95) {
        check(response, {
            'under limit: should be allowed (200)': (r) => r.status === 200,
        });
    } else if (__ITER > 105) {
        check(response, {
            'over limit: should be blocked (429)': (r) => r.status === 429,
        });
    }

    // No sleep - we want to hit rate limit quickly
}

export function setup() {
    console.log('');
    console.log('═══════════════════════════════════════════════════════════════');
    console.log('     🧪 QUICK RATE LIMIT TEST');
    console.log('═══════════════════════════════════════════════════════════════');
    console.log(`📍 Target: ${BASE_URL}`);
    console.log('📋 Plan: 150 rapid requests from single IP');
    console.log('📊 Expected: ~100 allowed, ~50 blocked');
    console.log('');
    console.log('⏱️  IP Rate Limit: 100 requests per minute');
    console.log('═══════════════════════════════════════════════════════════════');
    console.log('');
}

export function teardown(data) {
    console.log('');
    console.log('═══════════════════════════════════════════════════════════════');
    console.log('     📊 TEST COMPLETE');
    console.log('═══════════════════════════════════════════════════════════════');
    console.log('');
    console.log('🔍 To verify Redis counters, run:');
    console.log('');
    console.log('   docker exec redis redis-cli ZCARD "ratelimit:ip:127.0.0.1"');
    console.log('   docker exec redis redis-cli KEYS "ratelimit:*"');
    console.log('   docker exec redis redis-cli HGETALL "stats:endpoint:/api/v1/resource"');
    console.log('');
    console.log('═══════════════════════════════════════════════════════════════');
}
