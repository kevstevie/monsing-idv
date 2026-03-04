/**
 * GET /feedbacks/items/{id} — 피드백 상품 단건 스트레스 테스트
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/stress/solo/feedback-item-detail.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX , SUMMARY_TREND_STATS } from '../../config.js';
import { randomInt } from '../../helpers/utils.js';
import { recordDuration } from '../../helpers/metrics.js';
import { makeSummaryHandler } from '../../helpers/summary.js';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

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
  const id  = randomInt(FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX);
  const res = http.get(`${BASE_URL}/feedbacks/items/${id}`, { tags: { name: 'GET /feedbacks/items/{id}' } });

  recordDuration(res);
  check(res, { '5xx 없음': (r) => r.status < 500 });

  sleep(0.1);
}

export const handleSummary = makeSummaryHandler('GET /feedbacks/items/{id} 스트레스 테스트 (max 1000 VU)');
