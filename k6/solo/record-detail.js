/**
 * GET /records/{recordId} — record 단건 조회 (Student Auth) 단독 테스트
 *
 * 소유권 규칙 (seed_data.sql gen_records 기준):
 *   student_id = Math.floor((record_id - 1) / 10) + STUDENT_ID_MIN
 *   → 학생 1명당 record 10개 보유
 *
 * 응답에 feedbacks 배열 포함 → feedback 테이블 JOIN 발생
 * → records-my(목록) 대비 단건이지만 feedback 데이터가 추가되므로
 *    유사한 p(95)<300ms 임계값 적용
 *
 * 실행: k6 run --out influxdb=http://localhost:8086/k6 k6/solo/record-detail.js
 */
import http from 'k6/http';
import {check, sleep} from 'k6';
import {BASE_URL, JWT_SECRET, RECORD_ID_MAX, RECORD_ID_MIN, STUDENT_ID_MIN} from '../config.js';
import {authHeader, generateToken} from '../helpers/jwt.js';
import {randomInt} from '../helpers/utils.js';
import {recordDuration} from '../helpers/metrics.js';

// 2xx + 4xx → 성공 처리 / 5xx + 네트워크 에러만 http_req_failed 카운트
http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}));

// setup에서 생성할 (recordId, studentToken) 쌍 수
// VU 300 / 50쌍 = 쌍당 최대 6 VU 공유
const PAIR_COUNT = 50;

export const options = {
    stages: [
        {duration: '10s', target: 20},
        {duration: '30s', target: 200},
        {duration: '1m',  target: 300},
        {duration: '15s', target: 0},
    ],
    thresholds: {
        http_req_failed:              ['rate<0.001'],
        // 단건이지만 feedback JOIN 포함 → 목록 API와 동일 수준
        http_req_duration_success:    ['p(95)<300', 'p(99)<500'],
        http_req_duration_4xx:        ['p(95)<300', 'p(99)<500'],
    },
};

/**
 * seed 공식으로 record를 소유한 student_id를 역산한다.
 *   seed: student_id = Math.floor(idx / 10) + STUDENT_ID_MIN  (idx = record_id - 1)
 */
function ownerStudentId(recordId) {
    return Math.floor((recordId - 1) / 10) + STUDENT_ID_MIN;
}

export function setup() {
    return Array.from({length: PAIR_COUNT}, () => {
        const recordId  = randomInt(RECORD_ID_MIN, RECORD_ID_MAX);
        const studentId = ownerStudentId(recordId);
        return {recordId, token: generateToken(studentId, JWT_SECRET)};
    });
}

export default function (pairs) {
    const entry = pairs[(__VU - 1) % pairs.length];
    const res = http.get(
        `${BASE_URL}/records/${entry.recordId}`,
        {headers: authHeader(entry.token), tags: {name: 'GET /records/{id}'}}
    );

    recordDuration(res);

    check(res, {'record 200': (r) => r.status === 200});

    sleep(0.3);
}
