/**
 * GET /records/my — 내 record 목록 (Student Auth) 단독 테스트
 * 실행: k6 run k6/solo/records-my.js
 */
import http  from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, JWT_SECRET, STUDENT_ID_MIN, STUDENT_ID_MAX } from '../config.js';
import { generateToken, authHeader } from '../helpers/jwt.js';
import { randomInt } from '../helpers/utils.js';

const STUDENT_COUNT = 50;

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '30s', target: 100 },
    { duration: '1m',  target: 100 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed:   ['rate<0.01'],
  },
};

export function setup() {
  return Array.from({ length: STUDENT_COUNT }, () => {
    const id = randomInt(STUDENT_ID_MIN, STUDENT_ID_MAX);
    return { id, token: generateToken(id, JWT_SECRET) };
  });
}

export default function (studentTokens) {
  const entry = studentTokens[(__VU - 1) % studentTokens.length];
  const res   = http.get(
    `${BASE_URL}/records/my?size=20&lastId=0`,
    { headers: authHeader(entry.token), tags: { name: 'GET /records/my' } }
  );

  check(res, { 'my records 200': (r) => r.status === 200 });

  sleep(0.3);
}

export const handleSummary = makeSummaryHandler('GET /records/my — 내 학습 기록');
