/**
 * GET /courses?teacherId={id} — 강의 목록 조회 단독 테스트
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/solo/courses.js
 */
import http from 'k6/http';
import {check, sleep} from 'k6';
import {BASE_URL, TEACHER_ID_MAX, TEACHER_ID_MIN} from '../config.js';
import {randomInt} from '../helpers/utils.js';
import {recordDuration} from '../helpers/metrics.js';

// 2xx + 4xx → 성공 처리 / 5xx + 네트워크 에러만 http_req_failed 카운트
http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}));

export const options = {
    stages: [
        {duration: '10s', target: 10},
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
    const teacherId = randomInt(TEACHER_ID_MIN, TEACHER_ID_MAX);
    const res = http.get(`${BASE_URL}/courses?teacherId=${teacherId}`, {tags: {name: 'GET /courses'}});

    recordDuration(res);

    check(res, {
        '200 OK': (r) => r.status === 200,
    });

    sleep(0.3);
}
