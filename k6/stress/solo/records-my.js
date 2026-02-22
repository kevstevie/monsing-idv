/**
 * GET /records/my — 내 record 목록 스트레스 테스트 (Student Auth)
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/stress/solo/records-my.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, JWT_SECRET, STUDENT_ID_MIN } from '../../config.js';
import { generateToken, authHeader } from '../../helpers/jwt.js';
import { recordDuration } from '../../helpers/metrics.js';
import { makeSummaryHandler } from '../../helpers/summary.js';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

const STUDENT_POOL = 200;
const studentTokens = [];
for (let i = 0; i < STUDENT_POOL; i++) {
  studentTokens.push(generateToken(STUDENT_ID_MIN + i, JWT_SECRET));
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
  const token = studentTokens[(__VU - 1) % STUDENT_POOL];
  const res   = http.get(
    `${BASE_URL}/records/my?size=20&lastId=0`,
    { headers: authHeader(token), tags: { name: 'GET /records/my' } }
  );

  recordDuration(res);
  check(res, { '5xx 없음': (r) => r.status < 500 });

  sleep(0.1);
}

export const handleSummary = makeSummaryHandler('GET /records/my 스트레스 테스트 (max 1000 VU)');
