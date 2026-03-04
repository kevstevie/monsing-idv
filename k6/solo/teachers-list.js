/**
 * GET /teachers — 선생님 목록 조회 단독 테스트
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/solo/teachers-list.js
 */
import http from 'k6/http';
import {check, sleep} from 'k6';
import {BASE_URL, TEACHER_ID_MAX} from '../config.js';
import {randomCursor} from '../helpers/utils.js';
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
        http_req_duration_success: ['p(95)<300', 'p(99)<500'],
        http_req_duration_4xx: ['p(95)<300', 'p(99)<500'],
    },
};

export default function () {
    const url = `${BASE_URL}/teachers?size=20&lastId=${randomCursor(TEACHER_ID_MAX)}`;
    const res = http.get(url, {tags: {name: 'GET /teachers'}});

    recordDuration(res);

    check(res, {
        '200 OK': (r) => r.status === 200,
        'body not empty': (r) => r.body && r.body.length > 0,
    });

    sleep(0.3);
}
