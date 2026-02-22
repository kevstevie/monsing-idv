/**
 * GET /feedbacks/my         — 내 피드백 목록 (Teacher Auth)
 * GET /feedbacks/items/my   — 내 피드백 상품 목록 (Teacher Auth)
 * 실행: k6 run k6/solo/feedback-my.js
 */
import http  from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, JWT_SECRET } from '../config.js';
import { generateToken, authHeader } from '../helpers/jwt.js';
import { randomCursor } from '../helpers/utils.js';

const TEACHER_COUNT = 50;

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
  return Array.from({ length: TEACHER_COUNT }, (_, i) => ({
    id: i + 1,
    token: generateToken(i + 1, JWT_SECRET),
  }));
}

export default function (teacherTokens) {
  const entry   = teacherTokens[(__VU - 1) % teacherTokens.length];
  const headers = authHeader(entry.token);
  const lastId  = randomCursor(1_000_000);

  const myRes = http.get(
    `${BASE_URL}/feedbacks/my?size=20&lastId=${lastId}`,
    { headers, tags: { name: 'GET /feedbacks/my' } }
  );
  check(myRes, { 'my feedbacks 200': (r) => r.status === 200 });

  const myItemsRes = http.get(
    `${BASE_URL}/feedbacks/items/my?size=20&lastId=${lastId}`,
    { headers, tags: { name: 'GET /feedbacks/items/my' } }
  );
  check(myItemsRes, { 'my feedback items 200': (r) => r.status === 200 });

  sleep(0.3);
}

export const handleSummary = makeSummaryHandler('GET /feedbacks/my — 내 피드백');
