/**
 * GET /teachers — 선생님 목록 스트레스 테스트
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/stress/solo/teachers-list.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, TEACHER_ID_MAX } from '../../config.js';
import { randomCursor } from '../../helpers/utils.js';
import { recordDuration } from '../../helpers/metrics.js';
import { makeSummaryHandler } from '../../helpers/summary.js';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

export const options = {
  stages: [
    { duration: '30s', target: 100  },  // 워밍업
    { duration: '1m',  target: 300  },  // 부하
    { duration: '1m',  target: 500  },  // 스트레스
    { duration: '30s', target: 1000 },  // 스파이크
    { duration: '30s', target: 0    },  // 쿨다운
  ],
  thresholds: {
    http_req_failed:             ['rate<0.05'],
    http_req_duration_success:   ['p(95)<3000', 'p(99)<5000'],
    http_req_duration_4xx:       ['p(95)<3000', 'p(99)<5000'],
  },
};

export default function () {
  const url = `${BASE_URL}/teachers?size=20&lastId=${randomCursor(TEACHER_ID_MAX)}`;
  const res = http.get(url, { tags: { name: 'GET /teachers' } });

  recordDuration(res);
  check(res, { '5xx 없음': (r) => r.status < 500 });

  sleep(0.1);
}

export const handleSummary = makeSummaryHandler('GET /teachers 스트레스 테스트 (max 1000 VU)');
