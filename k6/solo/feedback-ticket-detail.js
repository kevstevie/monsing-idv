/**
 * GET /feedbacks/tickets/{ticketId} — feedback ticket 단건 조회 (Auth) 단독 테스트
 *
 * - 소유권 검증 없음 → 어떤 유효 토큰이든 접근 가능
 * - Teacher 토큰 사용 (setup에서 사전 생성)
 * - PK 단건 조회 → 단순하지만 JWT 검증 오버헤드 존재
 *   → feedback-item-detail(no-auth, p95<150)보다 완화된 임계값 적용
 *
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/solo/feedback-ticket-detail.js
 */
import http from 'k6/http';
import {check, sleep} from 'k6';
import {BASE_URL, FEEDBACK_TICKET_ID_MAX, FEEDBACK_TICKET_ID_MIN, JWT_SECRET} from '../config.js';
import {authHeader, generateToken} from '../helpers/jwt.js';
import {randomInt} from '../helpers/utils.js';
import {recordDuration} from '../helpers/metrics.js';

// 2xx + 4xx → 성공 처리 / 5xx + 네트워크 에러만 http_req_failed 카운트
http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}));

const TEACHER_COUNT = 50;

export const options = {
    stages: [
        {duration: '10s', target: 20},
        {duration: '30s', target: 200},
        {duration: '1m',  target: 300},
        {duration: '15s', target: 0},
    ],
    thresholds: {
        http_req_failed:           ['rate<0.001'],
        // Auth 포함 단건 PK 조회: no-auth(p95<150)보다 완화, 목록(p95<300)보다 엄격
        http_req_duration_success: ['p(95)<200', 'p(99)<400'],
        http_req_duration_4xx:     ['p(95)<200', 'p(99)<400'],
    },
};

export function setup() {
    return Array.from({length: TEACHER_COUNT}, (_, i) => ({
        id: i + 1,
        token: generateToken(i + 1, JWT_SECRET),
    }));
}

export default function (teacherTokens) {
    const entry    = teacherTokens[(__VU - 1) % teacherTokens.length];
    const ticketId = randomInt(FEEDBACK_TICKET_ID_MIN, FEEDBACK_TICKET_ID_MAX);
    const res = http.get(
        `${BASE_URL}/feedbacks/tickets/${ticketId}`,
        {headers: authHeader(entry.token), tags: {name: 'GET /feedbacks/tickets/{id}'}}
    );

    recordDuration(res);

    check(res, {'ticket 200 or 404': (r) => r.status === 200 || r.status === 404});

    sleep(0.3);
}
