/**
 * GET /feedbacks/items/{id} — 피드백 상품 단건 조회 단독 테스트
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/solo/feedback-item-detail.js
 */
import http from 'k6/http';
import {check, sleep} from 'k6';
import {BASE_URL, FEEDBACK_ITEM_ID_MAX, FEEDBACK_ITEM_ID_MIN} from '../config.js';
import {randomInt} from '../helpers/utils.js';
import {recordDuration} from '../helpers/metrics.js';

// 2xx + 4xx → 성공 처리 / 5xx + 네트워크 에러만 http_req_failed 카운트
http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}));

export const options = {
    stages: [
        {duration: '10s', target: 20},
        {duration: '30s', target: 200},
        {duration: '30s', target: 300},
        {duration: '30s', target: 400},
        {duration: '15s', target: 0},
    ],
    thresholds: {
        http_req_failed: ['rate<0.001'],
        http_req_duration_success: ['p(95)<150', 'p(99)<300'],
        http_req_duration_4xx: ['p(95)<150', 'p(99)<300'],
    },
};

export default function () {
    const id = randomInt(FEEDBACK_ITEM_ID_MIN, FEEDBACK_ITEM_ID_MAX);
    const res = http.get(`${BASE_URL}/feedbacks/items/${id}`, {tags: {name: 'GET /feedbacks/items/{id}'}});

    recordDuration(res);

    check(res, {
        '200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    sleep(0.3);
}
