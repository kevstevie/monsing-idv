# Chat Module 평가 및 개선점

> 분석 일자: 2026-01-28

## 전체 평가

| 항목 | 점수 | 비고 |
|------|------|------|
| 아키텍처 | ⭐⭐⭐ | 기본적인 레이어 분리 |
| 코드 품질 | ⭐⭐ | 몇 가지 문제점 존재 |
| 테스트 커버리지 | ⭐⭐⭐ | 핵심 로직 테스트 존재 |
| 확장성 | ⭐⭐ | 분산 환경 고려했으나 미완성 |

---

## Critical Issues

### 1. WebSocketMetrics.kt - 컴파일 에러

**파일**: `chat/src/main/kotlin/org/monsing/monitor/WebSocketMetrics.kt`

```kotlin
// 현재 코드 (Line 12)
private val meterRegistry: MeterRegistry  // import 누락!

// Prometheus Gauge 사용 중인데 MeterRegistry와 혼용
Gauge.builder("websocket.sessions") { ... }
    .register(meterRegistry)  // Prometheus API와 불일치
```

**문제**: `MeterRegistry` import 누락 + Prometheus/Micrometer API 혼용

**해결**: Micrometer로 통일하거나 Prometheus 직접 사용으로 변경

---

### 2. ChatInterceptor.kt - println 사용

**파일**: `chat/src/main/kotlin/org/monsing/api/ChatInterceptor.kt` (Line 43)

```kotlin
override fun afterHandshake(...) {
    println("afterHandshake")  // 프로덕션 코드에 println
}
```

**문제**: Detekt `ForbiddenMethodCall` 위반 가능, 로그 관리 불가

**해결**: Logger 사용 또는 제거

---

### 3. ChatService.kt - TODO 미구현

**파일**: `app/src/main/kotlin/org/monsing/chat/ChatService.kt` (Line 35-37)

```kotlin
private fun publishMessageSentEvent() {
    //TODO: Implement this method
}
```

**문제**: 오프라인 사용자 푸시 알림 미구현

**해결**: Kafka 이벤트 발행 또는 FCM 연동 구현

---

## Medium Issues

### 4. ChatService - 단일 책임 원칙 위반

**파일**: `app/src/main/kotlin/org/monsing/chat/ChatService.kt`

`ChatService`가 너무 많은 책임을 가짐 (16개 메서드):
- 세션 관리 (`saveSession`, `removeSession`)
- 메시지 전송 (`sendMessage`, `relayMessage`, `sendToOtherServer`)
- 채팅방 관리 (`createChat`, `joinChat`, `leaveChat`)
- 조회 (`getMessages`, `findChatByMemberId`, `findChatThumbnail`)

**개선안**: 역할별 서비스 분리

```
ChatService → ChatRoomService (채팅방 CRUD)
           → MessageService (메시지 처리)
           → SessionService (세션 관리)
           → MessageRelayService (서버 간 메시지 중계)
```

---

### 5. 하드코딩된 HTTP 통신

**파일**: `app/src/main/kotlin/org/monsing/chat/ChatService.kt` (Line 108-115)

```kotlin
private fun sendToOtherServer(receiverServerId: String, ...) {
    client.sendAsync(
        HttpRequest.newBuilder()
            .uri(URI.create("http://$receiverServerId/relay?receiverId=$receiverId"))
            // 타임아웃 없음, 에러 핸들링 없음
            ...
    )
}
```

**문제**:
- 타임아웃 설정 없음
- 에러 핸들링 없음
- 비동기 결과 무시

**개선안**: WebClient/Feign 사용 + Circuit Breaker 적용

---

### 6. GlobalExceptionHandler - 메시지 노출 위험

**파일**: `chat/src/main/kotlin/org/monsing/GlobalExceptionHandler.kt`

```kotlin
@ExceptionHandler
fun handleException(e: Exception): ResponseEntity<String> {
    return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(e.message)
}
```

**문제**: 내부 예외 메시지가 클라이언트에 노출됨

**개선안**: 로깅 후 generic 메시지 반환

---

### 7. relayMessage 엔드포인트 인증 없음

**파일**: `chat/src/main/kotlin/org/monsing/api/ChatController.kt`

```kotlin
@PostMapping("/relay")
fun relayMessage(  // @Auth 어노테이션 없음
    @RequestBody message: Message,
    @RequestParam receiverId: Long
) { ... }
```

**문제**: 서버 간 통신 인증 없음

**개선안**: 서버 간 인증 토큰 또는 내부 네트워크 제한

---

## Minor Issues

### 8. HttpLogger - 미사용 변수 (Detekt 감지)

**파일**: `chat/src/main/kotlin/org/monsing/log/HttpLogger.kt` (Line 85)

```
Private property `duration` is unused. [UnusedPrivateProperty]
```

---

### 9. LocalSessionStorage - 세션 누수 가능성

**파일**: `domain/src/main/kotlin/org/monsing/chat/session/LocalSessionStorage.kt`

```kotlin
fun removeSession(memberId: Long, deviceId: String) {
    storage.remove(createKey(memberId, deviceId))
    // globalServerIdStorage에서도 제거해야 함
}
```

**문제**: 세션 제거 시 `globalServerIdStorage`에서 제거 안함

**현재 위치**: `ChatService.removeSession()`에서 호출하지만 global 제거 누락

---

### 10. 테스트 - 통합 테스트 부족

- WebSocket 연결 테스트 없음
- Controller 테스트 없음
- 실제 Redis/MongoDB 통합 테스트 제한적

---

## 아키텍처 개선 제안

### 현재 구조

```
[Client] → [WebSocketHandler] → [ChatService] → [Repository]
                                      ↓
                               [Other Server via HTTP]
```

### 개선 구조

```
[Client] → [WebSocketHandler] → [MessageService]
                                      ↓
                               [Kafka/Redis Pub-Sub] ← 메시지 브로커
                                      ↓
                               [Other Servers]
                                      ↓
                               [FCM] ← 오프라인 유저
```

**장점**:
- HTTP 직접 호출 → 메시지 브로커로 변경하여 느슨한 결합
- 서버 장애 시 메시지 유실 방지
- 수평 확장 용이

---

## 우선순위 개선 목록

| 순위 | 이슈 | 긴급도 | 파일 |
|------|------|--------|------|
| 1 | WebSocketMetrics 컴파일 에러 수정 | 🔴 Critical | `WebSocketMetrics.kt` |
| 2 | println → Logger 변경 | 🟡 Medium | `ChatInterceptor.kt` |
| 3 | publishMessageSentEvent 구현 | 🟡 Medium | `ChatService.kt` |
| 4 | /relay 엔드포인트 인증 추가 | 🟡 Medium | `ChatController.kt` |
| 5 | Exception 메시지 노출 수정 | 🟡 Medium | `GlobalExceptionHandler.kt` |
| 6 | HttpLogger 미사용 변수 제거 | 🟢 Minor | `HttpLogger.kt` |
| 7 | ChatService 분리 (리팩토링) | 🟢 Minor | `ChatService.kt` |

---

## 모듈 구조

```
chat/
├── src/main/kotlin/org/monsing/
│   ├── api/
│   │   ├── ChatController.kt
│   │   ├── ChatInterceptor.kt
│   │   ├── WebSocketHandler.kt
│   │   ├── MemberMetadata.kt
│   │   ├── CreateChatRequest.kt
│   │   ├── MessageResponse.kt
│   │   └── ChatThumbnailResponse.kt
│   ├── config/
│   │   ├── JacksonConfig.kt
│   │   └── WebSocketConfig.kt
│   ├── log/
│   │   ├── LoggingFilter.kt
│   │   └── HttpLogger.kt
│   ├── monitor/
│   │   └── WebSocketMetrics.kt
│   ├── GlobalExceptionHandler.kt
│   └── MonsingChatApplication.kt
└── src/test/kotlin/org/monsing/
    ├── application/
    │   └── ChatServiceTest.kt
    ├── domain/
    │   ├── MessageRepositoryTest.kt
    │   └── session/
    │       └── GlobalServerIdStorageTest.kt
    └── support/
        ├── TestRedisConfig.kt
        └── SpringBootTestWithRedis.kt
```

---

## 의존성

```gradle
dependencies {
    implementation project(":app")
    implementation project(":domain")
    implementation project(":auth")

    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
}
```
