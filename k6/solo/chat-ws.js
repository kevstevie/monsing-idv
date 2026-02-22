/**
 * 채팅 WebSocket 동시 접속 + 메시지 전송 부하 테스트
 *
 * 흐름:
 *   setup()  → 선생님-학생 쌍으로 채팅방 생성 (POST /chats)
 *   default  → WebSocket 연결 → 메시지 전송 → 수신 확인 → 연결 종료
 *
 * 실행:
 *   k6 run k6/solo/chat-ws.js
 *   k6 run k6/solo/chat-ws.js -e CHAT_URL=http://localhost:8080
 */
import ws   from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CHAT_URL, CHAT_WS_URL, JWT_SECRET, TEACHER_ID_MIN, STUDENT_ID_MIN } from '../config.js';
import { generateToken, authHeader } from '../helpers/jwt.js';
import { randomUUID, JSON_HEADERS } from '../helpers/utils.js';

const PAIR_COUNT  = 20;   // 채팅방 쌍 수
const MSG_COUNT   = 5;    // VU당 전송 메시지 수
const HOLD_MS     = 8000; // WebSocket 유지 시간 (ms)

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '30s', target: 50 },
    { duration: '1m',  target: 50 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    ws_connecting:        ['p(95)<1000'],
    ws_session_duration:  ['p(95)<15000'],
    ws_msgs_sent:         ['count>0'],
    checks:               ['rate>0.95'],
  },
};

// ─── setup: 채팅방 사전 생성 ───────────────────────────────────
export function setup() {
  const pairs = [];

  for (let i = 0; i < PAIR_COUNT; i++) {
    const teacherId = TEACHER_ID_MIN + i;         // 1~20
    const studentId = STUDENT_ID_MIN + i;         // 500001~500020

    const studentToken = generateToken(studentId, JWT_SECRET);
    const teacherToken = generateToken(teacherId, JWT_SECRET);

    // 학생 계정으로 선생님과 채팅방 생성
    const res = http.post(
      `${CHAT_URL}/chats`,
      JSON.stringify({ memberId: teacherId }),
      { headers: { ...authHeader(studentToken), ...JSON_HEADERS } }
    );

    if (res.status !== 200 && res.status !== 201) {
      console.warn(`채팅방 생성 실패 (student=${studentId}, teacher=${teacherId}): ${res.status} ${res.body}`);
      continue;
    }

    const chatId = JSON.parse(res.body).id;
    if (!chatId) {
      console.warn(`chatId 없음: ${res.body}`);
      continue;
    }

    pairs.push({ chatId, studentToken, teacherToken, studentId, teacherId });
  }

  console.log(`채팅방 ${pairs.length}개 준비 완료`);
  return pairs;
}

// ─── VU 메인 루프 ─────────────────────────────────────────────
export default function (pairs) {
  if (!pairs || pairs.length === 0) {
    console.error('채팅방 데이터가 없습니다. setup() 실패를 확인하세요.');
    return;
  }

  const entry    = pairs[(__VU - 1) % pairs.length];
  const deviceId = randomUUID();
  const wsUrl    = `${CHAT_WS_URL}/ws/chat?token=${entry.studentToken}&device-id=${deviceId}`;

  let msgReceived = 0;

  const res = ws.connect(wsUrl, {}, function (socket) {
    socket.on('open', () => {
      // 메시지 순차 전송
      for (let i = 1; i <= MSG_COUNT; i++) {
        socket.send(JSON.stringify({
          chatId:  entry.chatId,
          content: `부하테스트 메시지 ${i} (VU=${__VU})`,
        }));
      }
    });

    socket.on('message', (data) => {
      msgReceived++;
      let msg;
      try { msg = JSON.parse(data); } catch (_) { return; }

      check(msg, {
        '수신 메시지: chatId 존재': (m) => !!m.chatId,
        '수신 메시지: content 존재': (m) => !!m.content,
        '수신 메시지: senderId 존재': (m) => m.senderId !== undefined,
      });
    });

    socket.on('error', (e) => {
      console.error(`WebSocket 오류 (VU=${__VU}): ${e.error()}`);
    });

    // 일정 시간 유지 후 종료
    socket.setTimeout(() => socket.close(), HOLD_MS);
  });

  check(res, {
    'WebSocket 연결 성공 (101)': (r) => r && r.status === 101,
  });

  sleep(0.5);
}

