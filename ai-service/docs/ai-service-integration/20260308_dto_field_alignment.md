# DTO 필드 정합성 분석 — ai-service ↔ video-service

> **목적**: 엔티티 + Kafka 이벤트 DTO의 ID 명칭·타입을 video-service에 맞춰 통일

---

## 0. Minutes 엔티티 PK 구조 변경

### 현행 → 변경

```diff
 // Minutes.java
-@Id
-@Column(name = "meeting_id", length = 64)
-private String meetingId;        // ❌ String PK, 비즈니스 키가 PK
+@Id
+@GeneratedValue(strategy = GenerationType.IDENTITY)
+private Long id;                 // ✅ auto-increment PK (video-service 패턴)
+
+@Column(name = "room_id", nullable = false)
+private Long roomId;             // ✅ video-service의 MeetingRoom.id와 동일
```

| 항목 | 현행 | 변경 후 |
|---|---|---|
| PK | `meeting_id` (String) | `id` (Long, auto-increment) |
| 회의 참조 | `meetingId` (String) | `roomId` (Long) |
| 1:N 지원 | ❌ 1:1 강제 | ✅ 같은 회의 여러 회의록 가능 |

---

## 1. ID 명칭 통일 규칙 — video-service 기준

| 개념 | video-service 명칭 | ai-service 현행 | ai-service 변경 후 |
|---|---|---|---|
| 회의 ID | `roomId` (`Long`) | `meetingId` (`String`) | **`roomId`** (`Long`) |
| 사용자 ID | `userId` (`Long`) | `participantId` (`String`) | **`userId`** (`Long`) |
| 발신자 ID | `senderId` (`Long`) | `senderId` (`String`) | **`senderId`** (`Long`) |
| 호스트 ID | `hostUserId` (`Long`) | 없음 | 필요시 추가 |
| 방 이름 | `roomName` (`String`) | 없음 | 필요시 추가 |

---

## 2. video-service 이벤트 전달 데이터 상세

### 2-1. `MeetingEvent` — 회의 시작/종료

```java
public record MeetingEvent(
    String type,        // "MEETING_STARTED" / "MEETING_ENDED"
    Long roomId,
    Long hostUserId,
    int participantCount,
    Instant startedAt,  // 시작 시 → now, 종료 시 → room.getStartedAt()
    Instant endedAt     // 시작 시 → null, 종료 시 → now
)
```

**실제 호출**:
```java
// 시작 시 (MeetingRoomService.java:292)
new MeetingEvent("MEETING_STARTED", roomId, userId, participantCount, now, null)

// 종료 시 (MeetingRoomService.java:325)
new MeetingEvent("MEETING_ENDED", roomId, userId, activeParticipants.size(),
    room.getStartedAt(), now)
```

### 2-2. `ParticipantEvent` — 참가자 입퇴장

```java
public record ParticipantEvent(
    String type,    // "PARTICIPANT_JOINED" / "PARTICIPANT_LEFT"
    Long roomId,
    Long userId,
    Instant timestamp
)
```

### 2-3. `AudioSegmentEvent` — 오디오 세그먼트

```java
public record AudioSegmentEvent(
    String type,       // "FULL_AUDIO" / "SEGMENT"
    Long roomId,
    int segmentIndex,
    String s3Path,
    String startTime,  // ⚠️ String 포맷 — 변환 필요
    String endTime
)
```

> [!WARNING]
> Track Egress 구현 시 `userId`, `trackSid`, `format` 필드 추가 필요

### 2-4. `ChatMessageEvent` — 채팅 메시지

```java
public record ChatMessageEvent(
    String messageId,
    Long roomId,
    String roomName,
    Long senderId,
    String senderIdentity,
    String messageType,      // "CHAT" / "FILE" 등
    String content,
    String replyToMessageId,
    Instant timestamp
)
```

**실제 호출** (ChatIntegrationService.java:134, 161):
```java
new ChatMessageEvent(
    messageId, roomId, room.getLivekitRoomName(),
    senderId, String.valueOf(senderId),
    messageType, request.content(),
    request.replyToMessageId(), message.timestamp()
)
```

### 2-5. `ScreenShareEvent` — 화면 공유

```java
public record ScreenShareEvent(
    String type,   // "SCREEN_SHARE_STARTED" / "SCREEN_SHARE_STOPPED"
    Long roomId,
    Long userId,
    Instant timestamp
)
```

---

## 3. ai-service Kafka 이벤트 DTO 변경 명세

### 3-1. `AudioChunkReadyEvent` ← `AudioSegmentEvent`

| # | 필드 | 현행 | 변경 후 | video 소스 |
|---|---|---|---|---|
| 1 | ~~meetingId~~ | `String` | **`roomId`** (`Long`) | `roomId` |
| 2 | ~~participantId~~ | `String` | **`userId`** (`Long`) | Track Egress identity |
| 3 | trackId | `String` | 유지 | trackSid |
| 4 | chunkSeq | `int` | 유지 | `segmentIndex` |
| 5 | chunkStartMs | `long` | 유지 | — |
| 6 | chunkEndMs | `long` | 유지 | — |
| 7 | audioFileKey | `String` | 유지 | `s3Path` |
| 8 | format | `String` | 유지 | `"ogg"` |
| 9 | ~~occurredAtEpochMs~~ | `long` | **`timestamp`** (`Instant`) | `Instant.now()` |

### 3-2. `ChatMessageEvent`

| # | 필드 | 현행 | 변경 후 | video 소스 |
|---|---|---|---|---|
| 1 | ~~meetingId~~ | `String` | **`roomId`** (`Long`) | `roomId` |
| 2 | messageId | `String` | 유지 | `messageId` |
| 3 | senderId | `String` | **`Long`** | `senderId` |
| 4 | ~~atMs~~ | `long` | **삭제** | `timestamp` 통합 |
| 5 | seq | `long` | 유지 | 생성 필요 |
| 6 | content | `String` | 유지 | `content` |
| 7 | ~~occurredAtEpochMs~~ | `long` | **`timestamp`** (`Instant`) | `timestamp` |

### 3-3. `MeetingEndedEvent`

| # | 필드 | 현행 | 변경 후 | video 소스 |
|---|---|---|---|---|
| 1 | ~~meetingId~~ | `String` | **`roomId`** (`Long`) | `roomId` |
| 2 | ~~endedAtEpochMs~~ | `long` | **`endedAt`** (`Instant`) | `endedAt` |
| — | (추가 고려) | — | `startedAt` (`Instant`) | `startedAt` |
| — | (추가 고려) | — | `hostUserId` (`Long`) | `hostUserId` |
| — | (추가 고려) | — | `participantCount` (`int`) | `participantCount` |

### 3-4. 내부 이벤트 DTO

| 이벤트 | 변경 필드 |
|---|---|
| `VoiceSegmentCreatedEvent` | `meetingId`→`roomId`(Long), `participantId`→`userId`(Long), `occurredAtEpochMs`→`timestamp`(Instant) |
| `TranscriptFinalizedEvent` | `meetingId`→`roomId`(Long), `finalizedAtEpochMs`→`finalizedAt`(Instant) |
| `MinutesGeneratedEvent` | `meetingId`→`roomId`(Long), `generatedAtEpochMs`→`generatedAt`(Instant) |
| `TranscriptDocument` | 내부 Event `atMs`(Long)→`timestamp`(Instant) |

---

## 4. 영향 범위 요약

### ai-service 변경 파일 목록

| 파일 | 변경 |
|---|---|
| **엔티티** ||
| `Minutes.java` | PK→`Long id`, `meetingId`→`roomId`(Long) |
| **Repository** ||
| `MinutesRepository.java` | `<Minutes, Long>`, `findByRoomId(Long)` |
| **Controller** ||
| `MinutesController.java` | `@PathVariable Long roomId` |
| **Service** ||
| `MinutesService.java` | meetingId→roomId 전체 |
| `SttWorkerService.java` | meetingId→roomId, participantId→userId |
| `TranscriptBuilderService.java` | meetingId→roomId, actorId→userId |
| `SummaryWorkerService.java` | meetingId→roomId |
| **DTO/Event** ||
| `AudioChunkReadyEvent.java` | meetingId→roomId, participantId→userId |
| `ChatMessageEvent.java` | meetingId→roomId, senderId Long |
| `MeetingEndedEvent.java` | meetingId→roomId |
| `VoiceSegmentCreatedEvent.java` | meetingId→roomId, participantId→userId |
| `TranscriptFinalizedEvent.java` | meetingId→roomId |
| `MinutesGeneratedEvent.java` | meetingId→roomId |
| **Response** ||
| `MinutesResponse.java` | `id`(Long) 추가, meetingId→roomId(Long) |
| **DB** ||
| Flyway 마이그레이션 | DDL 변경 SQL |

### video-service 변경 — Kafka 발행 시

| 작업 | 내용 |
|---|---|
| ID 필드 | `Long` 그대로 발행 (변환 불필요) |
| 시간 필드 | **`Instant`** 그대로 발행 (epochMs 변환 불필요) |
| `atMs`, `seq` | video에서 계산/생성 |
| Track Egress 필드 | `userId`, `trackSid`, `format` 추가 |

---

## 5. CC 위임용 프롬프트

> **[To Claude Code]**
> - **Role:** Senior Java/Spring Developer
> - **Goal:** ai-service의 ID 명칭을 video-service에 맞추고 Minutes 엔티티 PK 구조 변경
> - **Tech Spec:**
>   - `Minutes.java`: `@Id Long id` (auto-increment) + `Long roomId` (FK)
>   - 모든 DTO: `meetingId` → `roomId`(Long), `participantId` → `userId`(Long), `senderId` → Long
>   - 모든 시간 필드: `epochMs`(Long) → `java.time.Instant` (`timestamp`, `endedAt` 등 video-service 컨벤션 맞춤)
>   - `MinutesRepository`: `JpaRepository<Minutes, Long>` + `Optional<Minutes> findByRoomId(Long)`
>   - `MinutesController`: `@PathVariable Long roomId`
> - **Action Items:**
>   1. `Minutes.java` 엔티티 PK 구조 + roomId 명칭 변경
>   2. 이벤트 DTO 6개 ID 필드명/타입 변경 및 시간 타입 Instant 변경
>   3. Service 레이어 (MinutesService, SttWorkerService, TranscriptBuilderService, SummaryWorkerService) 내부 로직 수정
>   4. Controller + Response DTO 수정
>   5. Flyway 마이그레이션 SQL 작성
>   6. 연관된 Test 코드 전체 수정 및 검증
> - **Deliverable:** 변경된 전체 파일 + 마이그레이션
