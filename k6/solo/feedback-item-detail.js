/**
 * GET /feedbacks/items/{id} — 피드백 상품 단건 조회 단독 테스트
 * 실행: k6 run k6/solo/feedback-item-detail.js
 */
import http  from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX } from '../config.js';
import { randomInt } from '../helpers/utils.js';

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '30s', target: 50 },
    { duration: '1m',  target: 50 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
  const id  = randomInt(FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX);
  const res = http.get(`${BASE_URL}/feedbacks/items/${id}`, { tags: { name: 'GET /feedbacks/items/{id}' } });

  check(res, {
    '200 or 404': (r) => r.status === 200 || r.status === 404,
  });

  sleep(0.3);
}
