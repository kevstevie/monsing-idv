/**
 * GET /records/my — 내 record 목록 (Student Auth) 단독 테스트
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/solo/records-my.js
 */
import http from 'k6/http';
import {check, sleep} from 'k6';
import {BASE_URL, JWT_SECRET, STUDENT_ID_MAX, STUDENT_ID_MIN} from '../config.js';
import {authHeader, generateToken} from '../helpers/jwt.js';
import {randomInt} from '../helpers/utils.js';
import {recordDuration} from '../helpers/metrics.js';

// 2xx + 4xx → 성공 처리 / 5xx + 네트워크 에러만 http_req_failed 카운트
http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}));

const STUDENT_COUNT = 50;

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

export function setup() {
    return Array.from({length: STUDENT_COUNT}, () => {
        const id = randomInt(STUDENT_ID_MIN, STUDENT_ID_MAX);
        return {id, token: generateToken(id, JWT_SECRET)};
    });
}

export default function (studentTokens) {
    const entry = studentTokens[(__VU - 1) % studentTokens.length];
    const res = http.get(
        `${BASE_URL}/records/my?size=20&lastId=0`,
        {headers: authHeader(entry.token), tags: {name: 'GET /records/my'}}
    );

    recordDuration(res);

    check(res, {'my records 200': (r) => r.status === 200});

    sleep(0.3);
}
