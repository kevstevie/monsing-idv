/**
 * Monsing API 부하 테스트
 *
 * 실행:
 *   k6 run k6/main.js
 *   k6 run k6/main.js --out influxdb=http://localhost:8086/k6  (Grafana 연동)
 *   k6 run k6/main.js -e BASE_URL=http://localhost:8090
 *   k6 run k6/main.js -e JWT_SECRET=<secret>
 */

import { sleep }  from 'k6';
import { STAGES, THRESHOLDS, JWT_SECRET, STUDENT_ID_MIN, STUDENT_ID_MAX } from './config.js';
import { generateToken }    from './helpers/jwt.js';
import { randomInt }        from './helpers/utils.js';

import { listTeachers, getTeacherDetail } from './scenarios/teachers.js';
import { getCoursesByTeacher, getLessons } from './scenarios/courses.js';
import { getFeedbackItems, getFeedbackItemDetail, getMyFeedbacks, getFeedbackTickets } from './scenarios/feedbacks.js';
import { getMyContent } from './scenarios/students.js';

export const options = {
  stages: STAGES.default,
  thresholds: THRESHOLDS,
};

export function setup() {
  const studentIds = Array.from({ length: 20 }, () =>
    randomInt(STUDENT_ID_MIN, STUDENT_ID_MAX)
  );

  const studentTokens = studentIds.map((id) => ({
    id,
    token: generateToken(id, JWT_SECRET),
  }));

  const teacherTokens = Array.from({ length: 10 }, (_, i) => ({
    id: i + 1,
    token: generateToken(i + 1, JWT_SECRET),
  }));

  return { studentTokens, teacherTokens };
}

export default function ({ studentTokens, teacherTokens }) {
  const vuIdx        = (__VU - 1) % studentTokens.length;
  const studentEntry = studentTokens[vuIdx];
  const teacherEntry = teacherTokens[vuIdx % teacherTokens.length];

  // ── Public APIs ─────────────────────────────────────────────
  listTeachers();
  getTeacherDetail();
  getCoursesByTeacher();
  getLessons();
  getFeedbackItems();
  getFeedbackItemDetail();

  // ── Auth APIs ────────────────────────────────────────────────
  getMyContent(studentEntry.token);
  getMyFeedbacks(teacherEntry.token);
  getFeedbackTickets(teacherEntry.token);

  sleep(0.5);
}
