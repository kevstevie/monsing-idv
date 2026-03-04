// ─── 서버 설정 ────────────────────────────────────────────────
export const BASE_URL  = __ENV.BASE_URL  || 'http://localhost:8090';
export const CHAT_URL  = __ENV.CHAT_URL  || 'http://localhost:8080';
export const CHAT_WS_URL = ((__ENV.CHAT_URL || 'http://localhost:8080')).replace(/^http/, 'ws');

// ─── 시드 데이터 ID 범위 ───────────────────────────────────────
// seed_data.sql 기준
export const TEACHER_ID_MIN = 1;
export const TEACHER_ID_MAX = 500_000;

export const STUDENT_ID_MIN = 500_001;
export const STUDENT_ID_MAX = 2_000_000;

export const COURSE_ID_MIN = 1;
export const COURSE_ID_MAX = 1_500_000;

export const FEEDBACK_ITEM_ID_MIN = 1;
export const FEEDBACK_ITEM_ID_MAX = 500_000; // teacher당 1개

export const RECORD_ID_MIN = 1;
export const RECORD_ID_MAX = 15_000_000;       // gen_records: 15M

export const FEEDBACK_TICKET_ID_MIN = 1;
export const FEEDBACK_TICKET_ID_MAX = 7_500_000; // gen_tickets: 7.5M

// ─── JWT 설정 (application-app.yml 기준 - app 프로파일이 local보다 우선) ──
export const JWT_SECRET = __ENV.JWT_SECRET
  || 'xl32frB+bFvlJ4/lgWRSmCqufDua1fEFuX+XAI7Nki57y8a63RGfCFEfjGwG+ZR352FypLWyoLSVF58JwFQuAg==';
export const JWT_ALGORITHM = 'sha512'; // HS512 (secret >= 64 bytes)

// ─── 요약 통계 설정 ───────────────────────────────────────────
// handleSummary data에 p(99)를 포함시키기 위해 명시적으로 설정
export const SUMMARY_TREND_STATS = ['avg', 'p(90)', 'p(95)', 'p(99)', 'max'];

// ─── 부하 시나리오 단계 ────────────────────────────────────────
export const STAGES = {
  // 가벼운 워밍업 후 점진적 부하
  default: [
    { duration: '30s', target: 20 },   // 워밍업
    { duration: '1m',  target: 50 },   // 기본 부하
    { duration: '30s', target: 100 },  // 점진적 증가
    { duration: '2m',  target: 100 },  // 피크 유지
    { duration: '30s', target: 0 },    // 감소
  ],
};

// ─── 성능 임계값 ──────────────────────────────────────────────
export const THRESHOLDS = {
  // 전체 요청 p95 < 1s, 에러율 < 1%
  http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  http_req_failed:   ['rate<0.01'],

  // API별 세분화 임계값 (name 태그 기준)
  'http_req_duration{name:GET /teachers}':              ['p(95)<800'],
  'http_req_duration{name:GET /teachers/{id}}':         ['p(95)<500'],
  'http_req_duration{name:GET /courses}':               ['p(95)<800'],
  'http_req_duration{name:GET /courses/{id}/lessons}':  ['p(95)<800'],
  'http_req_duration{name:GET /feedbacks/items}':       ['p(95)<800'],
  'http_req_duration{name:GET /feedbacks/items/{id}}':  ['p(95)<800'],
  'http_req_duration{name:GET /feedbacks/my}':          ['p(95)<1000'],
  'http_req_duration{name:GET /feedbacks/items/my}':    ['p(95)<1000'],
  'http_req_duration{name:GET /feedbacks/tickets}':     ['p(95)<1000'],
  'http_req_duration{name:GET /my-lessons}':            ['p(95)<1000'],
  'http_req_duration{name:GET /records/my}':            ['p(95)<1000'],
};
