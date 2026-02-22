/**
 * GET /teachers/{id} — 선생님 상세 조회 단독 테스트
 * 실행: k6 run k6/solo/teacher-detail.js
 */
import http  from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, TEACHER_ID_MIN, TEACHER_ID_MAX } from '../config.js';
import { randomInt } from '../helpers/utils.js';

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '30s', target: 100 },
    { duration: '1m',  target: 100 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<150', 'p(99)<300'],
    http_req_failed:   ['rate<0.001'],
  },
};

export default function () {
  const id  = randomInt(TEACHER_ID_MIN, TEACHER_ID_MAX);
  const res = http.get(`${BASE_URL}/teachers/${id}`, { tags: { name: 'GET /teachers/{id}' } });

  check(res, {
    '200 or 404': (r) => r.status === 200 || r.status === 404,
  });

  sleep(0.3);
}

