/**
 * 채팅 WebSocket 스트레스 테스트 — 동시 연결 한계 탐색
 *
 * 목적: WebSocket 동시 연결 수 및 메시지 처리 한계 확인
 * 전략: 연결 유지 시간 단축 + VU 급격히 증가
 *
 * 실행:
 *   k6 run k6/stress/chat-stress.js
 *   k6 run k6/stress/chat-stress.js -e CHAT_URL=http://host:8080
 */
import ws   from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CHAT_URL, CHAT_WS_URL, JWT_SECRET, TEACHER_ID_MIN, STUDENT_ID_MIN } from '../config.js';
import { generateToken, authHeader } from '../helpers/jwt.js';
import { randomUUID, JSON_HEADERS } from '../helpers/utils.js';
import { makeSummaryHandler } from '../helpers/summary.js';

const PAIR_COUNT = 50;   // 사전 생성 채팅방 수 (solo 20 → stress 50)
const MSG_COUNT  = 10;   // VU당 전송 메시지 수 (solo 5 → stress 10)
const HOLD_MS    = 5000; // 연결 유지 시간 (solo 8s → stress 5s, 더 빠른 순환)

// ── 부하 시나리오 ──────────────────────────────────────────────────
//
//  0s ──── 30s ───── 1m30s ──── 2m30s ──── 3m ─── 3m30s
//  0 → 50  50 → 150  150 → 300  300 → 500  500 → 0
//
export const options = {
  stages: [
    { duration: '30s', target: 50  },  // 워밍업
    { duration: '1m',  target: 150 },  // 부하
    { duration: '1m',  target: 300 },  // 스트레스
    { duration: '30s', target: 500 },  // 스파이크
    { duration: '30s', target: 0   },  // 쿨다운
  ],
  thresholds: {
    ws_connecting:       ['p(95)<2000'],   // 연결 시간 완화 (solo 1s → 2s)
    ws_session_duration: ['p(95)<20000'],  // 세션 시간 완화 (solo 15s → 20s)
    ws_msgs_sent:        ['count>0'],
    checks:              ['rate>0.90'],    // 성공률 기준 완화 (solo 0.95 → 0.90)
  },
};

// ── setup: 채팅방 사전 생성 ──────────────────────────────────────
export function setup() {
  var pairs = [];

  for (var i = 0; i < PAIR_COUNT; i++) {
    var teacherId    = TEACHER_ID_MIN + i;
    var studentId    = STUDENT_ID_MIN + i;
    var studentToken = generateToken(studentId, JWT_SECRET);
    var teacherToken = generateToken(teacherId, JWT_SECRET);

    var res = http.post(
      CHAT_URL + '/chats',
      JSON.stringify({ memberId: teacherId }),
      { headers: Object.assign({}, authHeader(studentToken), JSON_HEADERS) }
    );

    if (res.status !== 200 && res.status !== 201) {
      console.warn('채팅방 생성 실패 (student=' + studentId + '): ' + res.status);
      continue;
    }

    var chatId = JSON.parse(res.body).id;
    if (!chatId) {
      console.warn('chatId 없음: ' + res.body);
      continue;
    }

    pairs.push({ chatId: chatId, studentToken: studentToken, teacherToken: teacherToken });
  }

  console.log('채팅방 ' + pairs.length + '개 준비 완료 (스트레스 테스트)');
  return pairs;
}

// ── VU 메인 루프 ────────────────────────────────────────────────
export default function (pairs) {
  if (!pairs || pairs.length === 0) {
    console.error('채팅방 데이터가 없습니다. setup() 실패를 확인하세요.');
    return;
  }

  var entry    = pairs[(__VU - 1) % pairs.length];
  var deviceId = randomUUID();
  var wsUrl    = CHAT_WS_URL + '/ws/chat?token=' + entry.studentToken + '&device-id=' + deviceId;

  var res = ws.connect(wsUrl, {}, function(socket) {
    socket.on('open', function() {
      for (var i = 1; i <= MSG_COUNT; i++) {
        socket.send(JSON.stringify({
          chatId:  entry.chatId,
          content: '스트레스 메시지 ' + i + ' (VU=' + __VU + ')',
        }));
      }
    });

    socket.on('message', function(data) {
      var msg;
      try { msg = JSON.parse(data); } catch(_) { return; }
      check(msg, {
        '수신: chatId 존재':   function(m) { return !!m.chatId; },
        '수신: content 존재':  function(m) { return !!m.content; },
      });
    });

    socket.on('error', function(e) {
      console.error('WS 오류 (VU=' + __VU + '): ' + e.error());
    });

    socket.setTimeout(function() { socket.close(); }, HOLD_MS);
  });

  check(res, {
    'WebSocket 101': function(r) { return r && r.status === 101; },
  });

  sleep(0.3);
}

export const handleSummary = makeSummaryHandler('채팅 WebSocket 스트레스 테스트 (max 500 VU)');
