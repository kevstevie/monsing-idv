/**
 * REST API 스트레스 테스트 — 한계점 탐색
 *
 * 목적: 서버가 몇 RPS에서 무너지는지 확인
 * 전략: VU를 1,000까지 점진 증가 후 스파이크, think time 최소화
 *
 * 실행:
 *   k6 run k6/stress/api-stress.js
 *   k6 run k6/stress/api-stress.js -e BASE_URL=http://host:8090
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  BASE_URL, JWT_SECRET,
  TEACHER_ID_MIN, TEACHER_ID_MAX,
  COURSE_ID_MIN,  COURSE_ID_MAX,
  FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX,
  STUDENT_ID_MIN, SUMMARY_TREND_STATS,
} from '../config.js';
import { generateToken, authHeader } from '../helpers/jwt.js';
import { randomInt, randomCursor } from '../helpers/utils.js';
import { makeSummaryHandler } from '../helpers/summary.js';

// ── 토큰 풀 (모듈 레벨 — VU 초기화 시 1회 생성) ─────────────────
const TEACHER_POOL = 100;
const STUDENT_POOL = 100;

const teacherTokens = [];
const studentTokens = [];

for (var t = 0; t < TEACHER_POOL; t++) {
  teacherTokens.push(generateToken(TEACHER_ID_MIN + t, JWT_SECRET));
}
for (var s = 0; s < STUDENT_POOL; s++) {
  studentTokens.push(generateToken(STUDENT_ID_MIN + s, JWT_SECRET));
}

// ── 부하 시나리오 ──────────────────────────────────────────────────
//
//  0s ──────── 30s ─────── 1m30s ────── 2m30s ─────── 3m ────── 3m30s
//  0 → 100VU  100 → 300VU  300 → 500VU  500 → 1000VU  1000 → 0
//
export const options = {
  summaryTrendStats: SUMMARY_TREND_STATS,
  stages: [
    { duration: '30s', target: 100  },  // 워밍업
    { duration: '1m',  target: 300  },  // 부하
    { duration: '1m',  target: 500  },  // 스트레스
    { duration: '30s', target: 1000 },  // 스파이크
    { duration: '30s', target: 0    },  // 쿨다운
  ],
  thresholds: {
    // 스트레스 테스트는 임계값을 완화 — 깨지는 지점 관찰이 목적
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
    http_req_failed:   ['rate<0.05'],
  },
};

// ── API 목록 (VU 인덱스로 순환) ──────────────────────────────────
function makeApis(tToken, sToken) {
  return [
    function() {
      return http.get(
        BASE_URL + '/teachers?lastId=' + randomCursor(TEACHER_ID_MAX) + '&size=20',
        { tags: { name: 'GET /teachers' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/teachers/' + randomInt(TEACHER_ID_MIN, TEACHER_ID_MAX),
        { tags: { name: 'GET /teachers/{id}' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/courses?teacherId=' + randomInt(TEACHER_ID_MIN, TEACHER_ID_MAX),
        { tags: { name: 'GET /courses' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/courses/' + randomInt(COURSE_ID_MIN, COURSE_ID_MAX) + '/lessons',
        { tags: { name: 'GET /courses/{id}/lessons' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/feedbacks/items?lastId=' + randomCursor(FEEDBACK_ITEM_ID_MAX) + '&size=20',
        { tags: { name: 'GET /feedbacks/items' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/feedbacks/items/' + randomInt(FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX),
        { tags: { name: 'GET /feedbacks/items/{id}' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/feedbacks/my?lastId=0&size=20',
        { headers: authHeader(tToken), tags: { name: 'GET /feedbacks/my' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/feedbacks/tickets?lastId=0&size=20',
        { headers: authHeader(tToken), tags: { name: 'GET /feedbacks/tickets' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/my-lessons?lastId=0&size=20',
        { headers: authHeader(sToken), tags: { name: 'GET /my-lessons' } }
      );
    },
    function() {
      return http.get(
        BASE_URL + '/records/my?lastId=0&size=20',
        { headers: authHeader(sToken), tags: { name: 'GET /records/my' } }
      );
    },
  ];
}

// ── VU 메인 루프 ─────────────────────────────────────────────────
export default function () {
  var idx    = (__VU - 1) % TEACHER_POOL;
  var tToken = teacherTokens[idx];
  var sToken = studentTokens[idx];

  var apis = makeApis(tToken, sToken);
  var api  = apis[(__VU - 1) % apis.length];
  var res  = api();

  check(res, { '5xx 없음': function(r) { return r.status < 500; } });

  // 스트레스 테스트: think time 최소화 (0.1s)
  sleep(0.1);
}

export const handleSummary = makeSummaryHandler('REST API 스트레스 테스트 (max 1000 VU)');
