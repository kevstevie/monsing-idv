import http    from 'k6/http';
import { check, group, sleep } from 'k6';
import { BASE_URL, TEACHER_ID_MIN, TEACHER_ID_MAX } from '../config.js';
import { randomInt, randomCursor } from '../helpers/utils.js';

const PAGE_SIZE = 20;

export function listTeachers() {
  group('teachers_list', () => {
    const lastId = randomCursor(TEACHER_ID_MAX);
    const url    = `${BASE_URL}/teachers?size=${PAGE_SIZE}&lastId=${lastId}`;
    const res    = http.get(url, { tags: { name: 'GET /teachers' } });

    check(res, {
      'teachers list 200': (r) => r.status === 200,
      'teachers list has body': (r) => r.body && r.body.length > 0,
    });

    sleep(randomInt(1, 3) * 0.1);
  });
}

export function getTeacherDetail() {
  group('teacher_detail', () => {
    const teacherId = randomInt(TEACHER_ID_MIN, TEACHER_ID_MAX);
    const res       = http.get(`${BASE_URL}/teachers/${teacherId}`, { tags: { name: 'GET /teachers/{id}' } });

    check(res, {
      'teacher detail 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    sleep(randomInt(1, 2) * 0.1);
  });
}
