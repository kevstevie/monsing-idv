/**
 * GET /feedbacks/items — 피드백 상품 목록 조회 단독 테스트
 * 실행: k6 run k6/solo/feedback-items.js
 */
import http  from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, TEACHER_ID_MIN, TEACHER_ID_MAX, FEEDBACK_ITEM_ID_MAX } from '../config.js';
import { randomInt, randomCursor } from '../helpers/utils.js';

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '30s', target: 100 },
    { duration: '1m',  target: 100 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<500'],
    http_req_failed:   ['rate<0.001'],
  },
};

export default function () {
  const teacherId = randomInt(TEACHER_ID_MIN, TEACHER_ID_MAX);
  const lastId    = randomCursor(FEEDBACK_ITEM_ID_MAX);
  const url       = `${BASE_URL}/feedbacks/items?teacherId=${teacherId}&size=20&lastId=${lastId}`;
  const res       = http.get(url, { tags: { name: 'GET /feedbacks/items' } });

  check(res, {
    '200 OK': (r) => r.status === 200,
  });

  sleep(0.3);
}

