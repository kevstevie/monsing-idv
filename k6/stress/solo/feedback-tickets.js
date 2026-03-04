/**
 * GET /feedbacks/tickets — 피드백 티켓 목록 스트레스 테스트 (Teacher Auth)
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/stress/solo/feedback-tickets.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, JWT_SECRET, TEACHER_ID_MIN , SUMMARY_TREND_STATS } from '../../config.js';
import { generateToken, authHeader } from '../../helpers/jwt.js';
import { randomCursor } from '../../helpers/utils.js';
import { recordDuration } from '../../helpers/metrics.js';
import { makeSummaryHandler } from '../../helpers/summary.js';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

const TEACHER_POOL = 200;
const teacherTokens = [];
for (let i = 0; i < TEACHER_POOL; i++) {
  teacherTokens.push(generateToken(TEACHER_ID_MIN + i, JWT_SECRET));
}

export const options = {
  summaryTrendStats: SUMMARY_TREND_STATS,  stages: [
    { duration: '30s', target: 100  },
    { duration: '1m',  target: 300  },
    { duration: '1m',  target: 500  },
    { duration: '30s', target: 1000 },
    { duration: '30s', target: 0    },
  ],
  thresholds: {
    http_req_failed:             ['rate<0.05'],
    http_req_duration_success:   ['p(95)<3000', 'p(99)<5000'],
    http_req_duration_4xx:       ['p(95)<500',  'p(99)<1000'],
  },
};

export default function () {
  const token  = teacherTokens[(__VU - 1) % TEACHER_POOL];
  const lastId = randomCursor(5_000_000);
  const res    = http.get(
    `${BASE_URL}/feedbacks/tickets?size=20&lastId=${lastId}`,
    { headers: authHeader(token), tags: { name: 'GET /feedbacks/tickets' } }
  );

  recordDuration(res);
  check(res, { '5xx 없음': (r) => r.status < 500 });

  sleep(0.1);
}

export const handleSummary = makeSummaryHandler('GET /feedbacks/tickets 스트레스 테스트 (max 1000 VU)');
