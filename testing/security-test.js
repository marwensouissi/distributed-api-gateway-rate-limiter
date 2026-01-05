import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: 10,
    duration: '10s',
};

export default function () {
    // Test Security Headers and Auth

    // 1. Valid API Key
    let resKey = http.get('http://localhost:8033/api/v1/resource', {
        headers: { 'X-API-KEY': 'secret-key' }
    });
    check(resKey, { 'API Key Valid': (r) => r.status === 200 });

    // 2. Invalid Auth (if we implemented blocking, checking status)
    // Currently passed through but analytics should see it.

    // 3. Brute Force Simulation
    // We hit endpoint rapidly. The IP limit is 100/min.
    // We expect 429s after 100 requests.

    for (let i = 0; i < 15; i++) {
        http.get('http://localhost:8033/api/v1/resource');
    }
}
