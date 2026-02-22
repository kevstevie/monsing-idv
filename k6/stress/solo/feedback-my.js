/**
 * GET /feedbacks/my + GET /feedbacks/items/my — 내 피드백 스트레스 테스트 (Teacher Auth)
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/stress/solo/feedback-my.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, JWT_SECRET, TEACHER_ID_MIN } from '../../config.js';
import { generateToken, authHeader } from '../../helpers/jwt.js';
import { randomCursor } from '../../helpers/utils.js';
import { recordDuration } from '../../helpers/metrics.js';
import { makeSummaryHandler } from '../../helpers/summary.js';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

// 모듈 레벨 토큰 풀 — VU 초기화 시 1회 생성
const TEACHER_POOL = 200;
const teacherTokens = [];
for (let i = 0; i < TEACHER_POOL; i++) {
  teacherTokens.push(generateToken(TEACHER_ID_MIN + i, JWT_SECRET));
}

export const options = {
  stages: [
    { duration: '30s', target: 100  },
    { duration: '1m',  target: 300  },
    { duration: '1m',  target: 500  },
    { duration: '30s', target: 1000 },
    { duration: '30s', target: 0    },
  ],
  thresholds: {
    http_req_failed:             ['rate<0.05'],
    http_req_duration_success:   ['p(95)<3000', 'p(99)<5000'],
    http_req_duration_4xx:       ['p(95)<3000', 'p(99)<5000'],
  },
};

export default function () {
  const token   = teacherTokens[(__VU - 1) % TEACHER_POOL];
  const headers = authHeader(token);
  const lastId  = randomCursor(1_000_000);

  const myRes = http.get(
    `${BASE_URL}/feedbacks/my?size=20&lastId=${lastId}`,
    { headers, tags: { name: 'GET /feedbacks/my' } }
  );
  recordDuration(myRes);
  check(myRes, { '5xx 없음': (r) => r.status < 500 });

  const myItemsRes = http.get(
    `${BASE_URL}/feedbacks/items/my?size=20&lastId=${lastId}`,
    { headers, tags: { name: 'GET /feedbacks/items/my' } }
  );
  recordDuration(myItemsRes);
  check(myItemsRes, { '5xx 없음': (r) => r.status < 500 });

  sleep(0.1);
}

export const handleSummary = makeSummaryHandler('GET /feedbacks/my + /items/my 스트레스 테스트 (max 1000 VU)');
