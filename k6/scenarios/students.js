import http    from 'k6/http';
import { check, group, sleep } from 'k6';
import { BASE_URL } from '../config.js';
import { randomInt } from '../helpers/utils.js';
import { authHeader } from '../helpers/jwt.js';

const PAGE_SIZE = 20;

/**
 * GET /my-lessons  — 내 수업 목록 (Auth)
 * GET /records/my  — 내 record 목록 (Auth)
 *
 * NOTE: GET /students/{id}/next-class, /students/{id}/courses 는
 *       서버에서 미구현(TODO) 상태로 제외
 */
export function getMyContent(token) {
  group('my_content', () => {
    const headers = authHeader(token);

    const lessonsRes = http.get(`${BASE_URL}/my-lessons`, {
      headers,
      tags: { name: 'GET /my-lessons' },
    });
    check(lessonsRes, { 'my lessons 200': (r) => r.status === 200 });

    const recordsRes = http.get(
      `${BASE_URL}/records/my?size=${PAGE_SIZE}&lastId=0`,
      { headers, tags: { name: 'GET /records/my' } }
    );
    check(recordsRes, { 'my records 200': (r) => r.status === 200 });

    sleep(randomInt(1, 2) * 0.1);
  });
}
