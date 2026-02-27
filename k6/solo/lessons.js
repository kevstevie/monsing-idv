/**
 * GET /courses/{id}/lessons — 수업 목록 조회 단독 테스트
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/solo/lessons.js
 */
import http from 'k6/http';
import {check, sleep} from 'k6';
import {BASE_URL, COURSE_ID_MAX, COURSE_ID_MIN} from '../config.js';
import {randomInt} from '../helpers/utils.js';
import {recordDuration} from '../helpers/metrics.js';

// 2xx + 4xx → 성공 처리 / 5xx + 네트워크 에러만 http_req_failed 카운트
http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}));

export const options = {
    stages: [
        {duration: '10s', target: 20},
        {duration: '30s', target: 200},
        {duration: '1m', target: 200},
        {duration: '10s', target: 0},
    ],
    thresholds: {
        http_req_failed: ['rate<0.001'],
        http_req_duration_success: ['p(95)<300', 'p(99)<500'],
        http_req_duration_4xx: ['p(95)<300', 'p(99)<500'],
    },
};

export default function () {
    const courseId = randomInt(COURSE_ID_MIN, COURSE_ID_MAX);
    const res = http.get(`${BASE_URL}/courses/${courseId}/lessons`, {tags: {name: 'GET /courses/{id}/lessons'}});

    recordDuration(res);

    check(res, {
        '200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    sleep(0.3);
}
