import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export let errorRate = new Rate('errors');

export let options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 1000, // 1000 req/s to start
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 1000,
      stages: [
        { target: 2000, duration: '10s' }, // Ramp to 2000 req/s
        { target: 2000, duration: '10s' },
        { target: 100, duration: '10s' }, // Ramp down
      ],
      startTime: '35s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<50'], // 95% of requests must complete below 50ms
    errors: ['rate<0.1'], // <10% errors
  },
};

export default function () {
  // 1. Valid Request
  let params = {
    headers: {
      'Authorization': 'Bearer valid-token',
      'Content-Type': 'application/json',
    },
    tags: { name: 'valid_request' },
  };

  let res = http.get('http://localhost:8033/api/v1/resource', params);
  console.log(res.body);

  check(res, {
    'status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  // 2. Unauthenticated (should be allowed but tracked, or blocked depending on policy)
  // Our impl allows pass-through but with empty user.

  // 3. Spam (Simulate High Rate)
  // This script itself acts as one "IP" effectively unless we distribute agents.
  // So eventually we expect 429 if the sliding window kicks in (Limit 100/min).
  // Note: Since K6 runs from one machine, it will hit the IP limit FAST.
}
