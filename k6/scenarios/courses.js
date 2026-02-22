import http    from 'k6/http';
import { check, group, sleep } from 'k6';
import { BASE_URL, TEACHER_ID_MIN, TEACHER_ID_MAX, COURSE_ID_MIN, COURSE_ID_MAX } from '../config.js';
import { randomInt } from '../helpers/utils.js';

export function getCoursesByTeacher() {
  group('courses', () => {
    const teacherId = randomInt(TEACHER_ID_MIN, TEACHER_ID_MAX);
    const res       = http.get(`${BASE_URL}/courses?teacherId=${teacherId}`, { tags: { name: 'GET /courses' } });

    check(res, {
      'courses by teacher 200': (r) => r.status === 200,
    });

    sleep(randomInt(1, 2) * 0.1);
  });
}

export function getLessons() {
  group('lessons', () => {
    const courseId = randomInt(COURSE_ID_MIN, COURSE_ID_MAX);
    const res      = http.get(`${BASE_URL}/courses/${courseId}/lessons`, { tags: { name: 'GET /courses/{id}/lessons' } });

    check(res, {
      'lessons 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    sleep(randomInt(1, 2) * 0.1);
  });
}
