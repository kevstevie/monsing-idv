/**
 * MongoDB 인덱스 초기화 스크립트
 *
 * 실행:
 *   mongosh mongodb://localhost:27017/chat scripts/mongo-indexes.js
 *
 * 또는 환경변수:
 *   MONGO_URI=mongodb://user:pass@host:27017/chat mongosh "$MONGO_URI" scripts/mongo-indexes.js
 */

// ─── memberChat 컬렉션 ─────────────────────────────────────────
// GET /chats → findChatByMemberId: { memberId: X }
db.memberChat.createIndex(
    { memberId: 1 },
    { name: "idx_memberChat_memberId" }
);

// existByChatId / deleteByChatIdAndMemberId / findOpponentId:
//   { chatId: X, memberId: Y }
// unique: 동일 채팅방에 같은 멤버가 중복 가입 방지
db.memberChat.createIndex(
    { chatId: 1, memberId: 1 },
    { name: "idx_memberChat_chatId_memberId", unique: true }
);

// ─── message 컬렉션 ────────────────────────────────────────────
// GET /chats/{id}/messages → findByChatId: { chatId: X, _id: { $lt: cursor } } sort _id DESC
// findLastMessageByChatId:  { chatId: X } sort _id DESC limit 1
db.message.createIndex(
    { chatId: 1, _id: -1 },
    { name: "idx_message_chatId_id_desc" }
);

// ─── 결과 확인 ──────────────────────────────────────────────────
print("\n=== 생성된 인덱스 ===");
print("\n[memberChat]");
db.memberChat.getIndexes().forEach(idx => printjson(idx));
print("\n[message]");
db.message.getIndexes().forEach(idx => printjson(idx));
print("\n인덱스 초기화 완료");
