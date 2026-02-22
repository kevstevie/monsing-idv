/**
 * 채팅 WebSocket 동시 접속 + 메시지 전송 부하 테스트
 *
 * 구조:
 *   student 시나리오 → 메시지 전송 (content에 sentAt 타임스탬프 포함)
 *   teacher 시나리오 → 메시지 수신 후 sentAt 파싱 → ws_msg_rtt 기록
 *
 * RTT 흐름:
 *   student: send(content="__ts:1234567890__ msg") → server → teacher: receive → RTT
 *
 * RPS 계산 (각 시나리오 50 VU 피크 기준):
 *   반복 주기 = HOLD_MS(8s) + sleep(0.5s) = 8.5s
 *   메시지 RPS = 50 VU × 5 messages / 8.5s ≈ 29 msg/s
 *
 * 실행:
 *   k6 run --out influxdb=http://localhost:8086/k6 k6/solo/chat-ws.js
 */
import ws   from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { CHAT_URL, CHAT_WS_URL, JWT_SECRET, TEACHER_ID_MIN, STUDENT_ID_MIN } from '../config.js';
import { generateToken, authHeader } from '../helpers/jwt.js';
import { randomUUID, JSON_HEADERS } from '../helpers/utils.js';

const PAIR_COUNT = 20;   // 채팅방 쌍 수
const MSG_COUNT  = 5;    // student VU당 전송 메시지 수
const HOLD_MS    = 8000; // WebSocket 유지 시간 (ms)
const TS_PREFIX  = '__ts:';

/** student 전송 → teacher 수신까지 end-to-end 왕복 시간 */
const msgRTT = new Trend('ws_msg_rtt', true);

export const options = {
  scenarios: {
    students: {
      executor: 'ramping-vus',
      exec:     'studentVu',
      stages: [
        { duration: '10s', target: 10 },
        { duration: '30s', target: 50 },
        { duration: '1m',  target: 50 },
        { duration: '10s', target: 0  },
      ],
    },
    teachers: {
      executor: 'ramping-vus',
      exec:     'teacherVu',
      stages: [
        { duration: '10s', target: 10 },
        { duration: '30s', target: 50 },
        { duration: '1m',  target: 50 },
        { duration: '10s', target: 0  },
      ],
    },
  },
  thresholds: {
    ws_connecting:       ['p(95)<1000', 'p(99)<2000'],
    ws_session_duration: ['p(95)<15000'],
    ws_msgs_sent:        ['count>0'],
    ws_msg_rtt:          ['p(95)<150',  'p(99)<300'],
    checks:              ['rate>0.95'],
  },
};

// ─── setup: 채팅방 사전 생성 ───────────────────────────────────
export function setup() {
  const pairs = [];

  for (let i = 0; i < PAIR_COUNT; i++) {
    const teacherId    = TEACHER_ID_MIN + i;
    const studentId    = STUDENT_ID_MIN + i;
    const studentToken = generateToken(studentId, JWT_SECRET);
    const teacherToken = generateToken(teacherId, JWT_SECRET);

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

    pairs.push({ chatId, studentToken, teacherToken });
  }

  console.log(`채팅방 ${pairs.length}개 준비 완료`);
  return pairs;
}

// ─── Student VU: 메시지 전송 ──────────────────────────────────
export function studentVu(pairs) {
  if (!pairs || pairs.length === 0) return;

  const entry    = pairs[(__VU - 1) % pairs.length];
  const deviceId = randomUUID();
  const wsUrl    = `${CHAT_WS_URL}/ws/chat?token=${entry.studentToken}&device-id=${deviceId}`;

  const res = ws.connect(wsUrl, {}, (socket) => {
    socket.on('open', () => {
      for (let i = 1; i <= MSG_COUNT; i++) {
        socket.send(JSON.stringify({
          chatId:  entry.chatId,
          content: `${TS_PREFIX}${Date.now()} 메시지 ${i}`,
        }));
      }
    });

    socket.on('error', (e) => {
      console.error(`Student WS 오류 (VU=${__VU}): ${e.error()}`);
    });

    socket.setTimeout(() => socket.close(), HOLD_MS);
  });

  check(res, {
    'Student WebSocket 101': (r) => r && r.status === 101,
  });

  sleep(0.5);
}

// ─── Teacher VU: 메시지 수신 + RTT 기록 ──────────────────────
export function teacherVu(pairs) {
  if (!pairs || pairs.length === 0) return;

  const entry    = pairs[(__VU - 1) % pairs.length];
  const deviceId = randomUUID();
  const wsUrl    = `${CHAT_WS_URL}/ws/chat?token=${entry.teacherToken}&device-id=${deviceId}`;

  const res = ws.connect(wsUrl, {}, (socket) => {
    socket.on('message', (data) => {
      let msg;
      try { msg = JSON.parse(data); } catch (_) { return; }

      // sentAt 파싱 → end-to-end RTT 기록
      if (msg.content && msg.content.startsWith(TS_PREFIX)) {
        const sentAt = parseInt(msg.content.slice(TS_PREFIX.length), 10);
        if (!isNaN(sentAt)) {
          msgRTT.add(Date.now() - sentAt);
        }
      }

      check(msg, {
        '수신 메시지: chatId 존재':   (m) => !!m.chatId,
        '수신 메시지: content 존재':  (m) => !!m.content,
        '수신 메시지: senderId 존재': (m) => m.senderId !== undefined,
      });
    });

    socket.on('error', (e) => {
      console.error(`Teacher WS 오류 (VU=${__VU}): ${e.error()}`);
    });

    // student보다 2초 더 유지해 마지막 메시지까지 수신
    socket.setTimeout(() => socket.close(), HOLD_MS + 2000);
  });

  check(res, {
    'Teacher WebSocket 101': (r) => r && r.status === 101,
  });

  sleep(0.5);
}
