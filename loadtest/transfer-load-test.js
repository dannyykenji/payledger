import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 20,
  duration: '15s',
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  const suffix = Date.now();
  const aliceRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({
      name: 'Alice LoadTest',
      email: `alice.${suffix}@loadtest.dev`,
      document: String(10000000000 + (suffix % 89999999999)),
      password: 'Password123!',
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const bobRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({
      name: 'Bob LoadTest',
      email: `bob.${suffix}@loadtest.dev`,
      document: String(20000000000 + (suffix % 79999999999)),
      password: 'Password123!',
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const alice = aliceRes.json();
  const bob = bobRes.json();

  http.post(
    `${BASE_URL}/api/v1/wallets/deposit`,
    JSON.stringify({ amount: 1000000.0, description: 'Seed k6' }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${alice.accessToken}`,
        'Idempotency-Key': `k6-seed-${suffix}`,
      },
    }
  );

  return { token: alice.accessToken, payeeWalletId: bob.walletId };
}

export default function (data) {
  const key = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const res = http.post(
    `${BASE_URL}/api/v1/transfers`,
    JSON.stringify({
      payeeWalletId: data.payeeWalletId,
      amount: 1.0,
      description: 'k6 load test transfer',
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.token}`,
        'Idempotency-Key': key,
      },
    }
  );

  check(res, {
    'status is 201': (r) => r.status === 201,
  });
}
