/**
 * GET /courses/{id}/lessons — 수업 목록 조회 단독 테스트
 * 실행: k6 run k6/solo/lessons.js
 */
import http  from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, COURSE_ID_MIN, COURSE_ID_MAX } from '../config.js';
import { randomInt } from '../helpers/utils.js';

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
  const courseId = randomInt(COURSE_ID_MIN, COURSE_ID_MAX);
  const res      = http.get(`${BASE_URL}/courses/${courseId}/lessons`, { tags: { name: 'GET /courses/{id}/lessons' } });

  check(res, {
    '200 or 404': (r) => r.status === 200 || r.status === 404,
  });

  sleep(0.3);
}

