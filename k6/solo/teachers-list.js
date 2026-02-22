/**
 * GET /teachers — 선생님 목록 조회 단독 테스트
 * 실행: k6 run k6/solo/teachers-list.js
 */
import http  from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, TEACHER_ID_MAX } from '../config.js';
import { randomCursor } from '../helpers/utils.js';

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
  const url = `${BASE_URL}/teachers?size=20&lastId=${randomCursor(TEACHER_ID_MAX)}`;
  const res = http.get(url, { tags: { name: 'GET /teachers' } });

  check(res, {
    '200 OK':       (r) => r.status === 200,
    'body not empty': (r) => r.body && r.body.length > 0,
  });

  sleep(0.3);
}
