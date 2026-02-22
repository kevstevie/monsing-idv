/**
 * WebSocket 채팅 스트레스 테스트 — 동시 연결 한계 탐색
 *
 * RPS 계산 (500 VU 피크 기준):
 *   반복 주기 = HOLD_MS(5s) + sleep(0.3s) = 5.3s
 *   메시지 RPS = 500 VU × 10 messages / 5.3s ≈ 943 msg/s
 *
 * RTT 측정 방식:
 *   senderId 필터링으로 내가 보낸 메시지 echo만 측정.
 *   여러 VU가 같은 채팅방 공유 시 타 VU 메시지가 먼저 도착해
 *   FIFO 큐가 오염되는 문제를 방지한다.
 *
 * 실행:
 *   k6 run --out influxdb=http://localhost:8086/k6 k6/stress/solo/chat-ws.js
 */
import ws   from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { CHAT_URL, CHAT_WS_URL, JWT_SECRET, TEACHER_ID_MIN, STUDENT_ID_MIN } from '../../config.js';
import { generateToken, authHeader } from '../../helpers/jwt.js';
import { randomUUID, JSON_HEADERS } from '../../helpers/utils.js';
import { makeSummaryHandler } from '../../helpers/summary.js';

const PAIR_COUNT = 50;   // 사전 생성 채팅방 수
const MSG_COUNT  = 10;   // VU당 전송 메시지 수
const HOLD_MS    = 5000; // 연결 유지 시간 (ms)

/** 메시지 왕복 시간 (전송 → 내 senderId echo 수신) */
const msgRTT = new Trend('ws_msg_rtt', true);

export const options = {
  stages: [
    { duration: '30s', target: 50  },
    { duration: '1m',  target: 150 },
    { duration: '1m',  target: 300 },
    { duration: '30s', target: 500 },
    { duration: '30s', target: 0   },
  ],
  thresholds: {
    ws_connecting:       ['p(95)<2000', 'p(99)<4000'],
    ws_session_duration: ['p(95)<20000'],
    ws_msgs_sent:        ['count>0'],
    ws_msg_rtt:          ['p(95)<500',  'p(99)<1000'],
    checks:              ['rate>0.90'],
  },
};

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
      console.warn(`채팅방 생성 실패 (student=${studentId}): ${res.status}`);
      continue;
    }

    const chatId = JSON.parse(res.body).id;
    if (!chatId) {
      console.warn(`chatId 없음: ${res.body}`);
      continue;
    }

    // studentId 포함 — senderId 필터링에 사용
    pairs.push({ chatId, studentToken, teacherToken, studentId });
  }

  console.log(`채팅방 ${pairs.length}개 준비 완료 (WebSocket 스트레스 테스트)`);
  return pairs;
}

export default function (pairs) {
  if (!pairs || pairs.length === 0) {
    console.error('채팅방 데이터가 없습니다. setup() 실패를 확인하세요.');
    return;
  }

  const entry    = pairs[(__VU - 1) % pairs.length];
  const deviceId = randomUUID();
  const wsUrl    = `${CHAT_WS_URL}/ws/chat?token=${entry.studentToken}&device-id=${deviceId}`;

  // 내가 보낸 메시지의 전송 시각 큐 (senderId 필터로만 shift)
  const sentTimes = [];

  const res = ws.connect(wsUrl, {}, (socket) => {
    socket.on('open', () => {
      for (let i = 1; i <= MSG_COUNT; i++) {
        sentTimes.push(Date.now());
        socket.send(JSON.stringify({
          chatId:  entry.chatId,
          content: `스트레스 메시지 ${i} (VU=${__VU})`,
        }));
      }
    });

    socket.on('message', (data) => {
      let msg;
      try { msg = JSON.parse(data); } catch (_) { return; }

      // 내가 보낸 메시지 echo만 RTT 측정 (타 VU 메시지 무시)
      if (msg.senderId === entry.studentId && sentTimes.length > 0) {
        msgRTT.add(Date.now() - sentTimes.shift());
      }

      check(msg, {
        '수신: chatId 존재':  (m) => !!m.chatId,
        '수신: content 존재': (m) => !!m.content,
      });
    });

    socket.on('error', (e) => {
      console.error(`WS 오류 (VU=${__VU}): ${e.error()}`);
    });

    socket.setTimeout(() => socket.close(), HOLD_MS);
  });

  check(res, {
    'WebSocket 101': (r) => r && r.status === 101,
  });

  sleep(0.3);
}

export const handleSummary = makeSummaryHandler('WebSocket 채팅 스트레스 테스트 (max 500 VU)');
