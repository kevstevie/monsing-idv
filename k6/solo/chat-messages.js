/**
 * 채팅 REST API 부하 테스트
 *   GET /chats              — 내 채팅방 목록
 *   GET /chats/{id}/messages — 채팅 메시지 히스토리 (커서 기반)
 *
 * 실행:
 *   k6 run k6/solo/chat-messages.js
 *   k6 run k6/solo/chat-messages.js -e CHAT_URL=http://localhost:8080
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CHAT_URL, JWT_SECRET, TEACHER_ID_MIN, STUDENT_ID_MIN } from '../config.js';
import { generateToken, authHeader } from '../helpers/jwt.js';
import { JSON_HEADERS } from '../helpers/utils.js';

const PAIR_COUNT = 20;
const PAGE_SIZE  = 20;

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '30s', target: 100 },
    { duration: '1m',  target: 100 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed:   ['rate<0.01'],
  },
};

// ─── setup: 채팅방 생성 및 chatId 수집 ───────────────────────
export function setup() {
  const pairs = [];

  for (let i = 0; i < PAIR_COUNT; i++) {
    const teacherId    = TEACHER_ID_MIN + i;
    const studentId    = STUDENT_ID_MIN + i;
    const studentToken = generateToken(studentId, JWT_SECRET);

    const res = http.post(
      `${CHAT_URL}/chats`,
      JSON.stringify({ memberId: teacherId }),
      { headers: { ...authHeader(studentToken), ...JSON_HEADERS } }
    );

    if (res.status !== 200 && res.status !== 201) {
      console.warn(`채팅방 생성 실패 (student=${studentId}): ${res.status}`);
      continue;
    }

    const chatId = JSON.parse(res.body).id;
    if (chatId) {
      pairs.push({ chatId, studentToken });
    }
  }

  console.log(`채팅방 ${pairs.length}개 준비 완료`);
  return pairs;
}

// ─── VU 메인 루프 ─────────────────────────────────────────────
export default function (pairs) {
  if (!pairs || pairs.length === 0) {
    console.error('채팅방 데이터가 없습니다.');
    return;
  }

  const entry   = pairs[(__VU - 1) % pairs.length];
  const headers = authHeader(entry.studentToken);

  // 내 채팅방 목록 조회
  const listRes = http.get(
    `${CHAT_URL}/chats`,
    { headers, tags: { name: 'GET /chats' } }
  );
  check(listRes, { 'chat list 200': (r) => r.status === 200 });

  // 특정 채팅방 메시지 히스토리 조회
  const msgRes = http.get(
    `${CHAT_URL}/chats/${entry.chatId}/messages?size=${PAGE_SIZE}`,
    { headers, tags: { name: 'GET /chats/{id}/messages' } }
  );
  check(msgRes, { 'chat messages 200': (r) => r.status === 200 });

  sleep(0.3);
}

export const handleSummary = makeSummaryHandler('GET /chats + /chats/{id}/messages — 채팅 REST');
