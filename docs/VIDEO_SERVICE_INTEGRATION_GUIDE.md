# Video Service - 타 서비스 연동 가이드

Video Service 내에서 타 서비스(User, Team, Notification, Minutes)의 연동 현황과 미완료 작업을 정리한 문서입니다.

> **최종 업데이트**: 2026-03-14
> **구현 클라이언트**: `AuthServiceClient` (RestTemplate 기반, `X-Gateway-Secret` 인증)
> 위치: `video-service/src/main/java/com/onmeet/video/infra/auth/`

---

## 전체 흐름도

```
Client (React)
      │
      ├── REST API ──► Gateway ──► Video Service
      │                                │
      │                  ┌─────────────┼─────────────────┐
      │                  │             │                  │
      │            User Service   Team Service    Notification Service
      │           (회원 정보 조회)  (팀 멤버십 검증)   (알림 발송)
      │            ✅ 완료          ✅ 완료           ⚠️ 부분 완료
      │                                │
      │                          Minutes Service
      │                         (회의록/STT 처리)
      │                          ❌ 미구현
      │
      └── WebRTC ──► LiveKit Server
```

---

## 1. User Service 연동 — ✅ 구현 완료

### 구현 현황

`AuthServiceClient`를 통해 auth-service의 Internal API를 호출하며, 모든 연동 포인트가 구현 완료되었습니다.

### 사용 중인 API (Video → Auth Internal)

| 용도 | 실제 요청 | 메서드 |
|------|----------|--------|
| 단일 사용자 조회 | `GET /auth/internal/users/{userId}` | `getUserInfo()` |
| 다수 사용자 일괄 조회 | `POST /auth/internal/users/batch` | `getBatchUserInfo()` |
| 사용자 존재 여부 확인 | `GET /auth/internal/users/{userId}/exists` | `userExists()` |
| 다수 사용자 존재 여부 일괄 확인 | `POST /auth/internal/users/exists/batch` | `batchUserExists()` |

### 연동 완료 내역

| 파일 | 메서드 | 구현 방식 | Fallback |
|------|--------|----------|----------|
| `MeetingRoomService` | `join()` | `getUserInfo()` → 실제 이름으로 LiveKit 토큰 발급 | `"user-" + userId` |
| `MeetingRoomService` | `getTimeline()` | `getBatchUserInfo()` → 타임라인에 실제 이름 표시 | `"User " + userId` |
| `RoomParticipantService` | `admitWaiting()` | `getUserInfo()` → 대기실 승인 시 실제 이름 | `"user-" + userId` |
| `RoomParticipantService` | `admitAllWaiting()` | `getBatchUserInfo()` → 일괄 승인 시 각 참가자 이름 | `"user-" + userId` |
| `ChatIntegrationService` | `sendMessage()` | `getUserInfo()` → 채팅 senderName에 실제 이름 | `"user-" + senderId` |
| `ScreenShareService` | `publishDataChannelMessage()` | `getUserInfo()` → DataChannel senderName에 실제 이름 | `"user-" + userId` |
| `RoomInvitationService` | `invite()` | `userExists()` → 초대 대상 존재 여부 검증 | 예외 throw |
| `RoomInvitationService` | `inviteBulk()` | `batchUserExists()` → 다수 사용자 존재 여부 일괄 검증 | 예외 throw |

---

## 2. Team Service 연동 — ✅ 구현 완료

### 구현 현황

`AuthServiceClient`를 통해 auth-service의 Internal API를 호출하며, 팀 접근 제어가 완전히 구현되어 있습니다.

### 사용 중인 API (Video → Auth Internal)

| 용도 | 실제 요청 | 메서드 |
|------|----------|--------|
| 팀 존재 확인 | `GET /auth/internal/teams/{teamId}/exists` | `teamExists()` |
| 팀 멤버십 확인 | `POST /auth/internal/teams/membership/check` | `isTeamMember()` |

### 연동 완료 내역

| 파일 | 메서드 | 구현 방식 |
|------|--------|----------|
| `MeetingRoomService` | `create()` | TEAM 방 생성 시 `teamExists()` + `isTeamMember()` 검증 |
| `MeetingRoomService` | `join()` | TEAM 방 참가 시 `isTeamMember()` 검증 (호스트 본인 제외) |
| `MeetingRoomService` | `schedule()` | 예약 회의 생성 시 동일하게 팀 검증 |
| `MeetingRoomService` | `validateAccessScope()` | `teamId == null` 여부만 검증 (상세 검증은 호출부에서 처리) |

---

## 3. Notification Service 연동 — ⚠️ 부분 완료

### 구현 현황

알림 발행 경로가 **두 개** 공존합니다:

| Publisher | 토픽 | 실제 동작 | 사용 위치 |
|-----------|------|----------|----------|
| `NotificationEventPublisher` | `notification.send` (Kafka) | **실제 발행** | 초대, 대기실, 리마인더 |
| `NoOpMeetingEventPublisher` | 없음 (로그만) | **미동작** | 회의 시작/종료/참가/강퇴 |

> **참고**: 문서 초안에서는 `meeting-events` 토픽을 사용하도록 기술했으나, 실제 구현은 `notification.send` 토픽을 사용합니다.

### 구현 완료 (Kafka `notification.send` 토픽)

| 파일 | 메서드 | 이벤트 타입 |
|------|--------|-----------|
| `RoomInvitationService` | `invite()` | `MEETING_INVITATION` |
| `RoomInvitationService` | `inviteBulk()` | `MEETING_INVITATION` (벌크) |
| `RoomInvitationService` | `accept()` | `INVITATION_ACCEPTED` |
| `RoomInvitationService` | `decline()` | `INVITATION_DECLINED` |
| `RoomInvitationService` | `cancelInvitation()` | `INVITATION_CANCELLED` |
| `RoomParticipantService` | `admitWaiting()` | `WAITING_ROOM_ADMITTED` |
| `RoomParticipantService` | `rejectWaiting()` | `WAITING_ROOM_REJECTED` |
| `MeetingNotificationScheduler` | 10분 주기 스케줄 | `MEETING_REMINDER` (30분 내 예약 회의) |

### 미구현 (NoOp — 로그만 출력, Kafka 발행 안 됨)

| 파일 | 메서드 | 필요한 이벤트 | 비고 |
|------|--------|-------------|------|
| `MeetingRoomService` | `start()` | `MEETING_STARTED` | `NoOpMeetingEventPublisher` → 로그만 |
| `MeetingRoomService` | `end()` | `MEETING_ENDED` | `NoOpMeetingEventPublisher` → 로그만 |
| `MeetingRoomService` | `join()` | `PARTICIPANT_JOINED` | `NoOpMeetingEventPublisher` → 로그만 |
| `RoomParticipantService` | `kick()` | `PARTICIPANT_KICKED` | `NoOpMeetingEventPublisher` → 로그만 |
| `MeetingRoomService` | `schedule()` | `SCHEDULE_CREATED` | 이벤트 발행 자체가 없음 |
| `MeetingRoomService` | `updateSchedule()` | `SCHEDULE_UPDATED` | 이벤트 발행 자체가 없음 |
| `MeetingRoomService` | `cancelSchedule()` | `SCHEDULE_CANCELLED` | 이벤트 발행 자체가 없음 |

### 알려진 이슈

1. **`MeetingEventPublisher`의 Kafka 구현체가 없음** — `NoOpMeetingEventPublisher`만 Bean으로 등록되어 회의 시작/종료/참가/강퇴 이벤트가 실제로 전달되지 않음
2. **`sendReminder()` 이벤트 타입 오용** — `publishMeetingStarted(new MeetingEvent("MEETING_REMINDER", ...))` 호출하지만 NoOp이라 무의미. 실제 리마인더는 `MeetingNotificationScheduler`가 처리
3. **`video-service/docker-compose.yml`에 Kafka 설정 누락** — `SPRING_KAFKA_BOOTSTRAP_SERVERS` 환경변수와 `depends_on: kafka` 미설정

---

## 4. Minutes Service (회의록) 연동 — ❌ 미구현

### 현재 상태

인터페이스와 이벤트 데이터 클래스는 설계되어 있으나, 실제 Kafka 발행 구현체와 ai-service 연동 코드가 없습니다.

| 구성 요소 | 상태 |
|----------|------|
| `MeetingEventPublisher` 인터페이스 | 선언됨 (`publishMeetingEnded`, `publishAudioSegmentReady`) |
| `MeetingEvent` 레코드 | 존재 (type, roomId, hostUserId, participantCount, startedAt, endedAt) |
| `AudioSegmentEvent` 레코드 | 존재 (s3Path, segmentIndex, startTime, endTime) |
| Kafka 구현체 | **없음** — `NoOpMeetingEventPublisher`만 존재 |
| ai-service REST 클라이언트 | **없음** |

### 미구현 연동 포인트

| 파일 | 메서드 | 필요 작업 | TODO 주석 |
|------|--------|----------|----------|
| `MeetingRoomService` | `end()` | 회의 메타데이터(참가자, 시간, 녹음 경로)를 ai-service로 전달 | 344번째 줄에 TODO 남아있음 |
| `RoomRecordingService` | `handleEgressEnded()` | 녹음 완료 시 S3 경로를 ai-service로 전달 | TODO 없음 (DB 저장만 수행) |

### 필요한 데이터

| 시점 | 전달 데이터 |
|------|-------------|
| 회의 종료 시 | roomId, 참가자 목록, 시작/종료 시간, 녹음 파일 경로 |
| FULL_AUDIO 녹음 완료 시 | roomId, S3 경로, 파일 크기 (전체 오디오 → STT 용) |
| SEGMENT 녹음 완료 시 | roomId, S3 경로, segmentIndex (실시간 전사 용) |

### 연동 흐름 (구현 필요)

```
[회의 종료]
      │
      ├── 1. 모든 참가자 퇴장 처리
      ├── 2. MEETING_ENDED 이벤트 발행 → Kafka 구현체 필요
      └── 3. Minutes Service에 회의 정보 전달 ──► ai-service
                                                       │
                                                       └── 회의록 생성 시작

[녹음 완료 (Egress)]
      │
      ├── 1. LiveKit Webhook: egress_ended
      ├── 2. DB에 S3 경로, 파일 크기 저장 (구현 완료)
      └── 3. Minutes Service에 오디오 전달 ──► ai-service (미구현)
                                                       │
                                                       └── STT 변환 시작
```

---

## 5. 화면 공유 (Screen Share) — ✅ 구현 완료

### 개요
회의 참가자가 화면 공유를 시작/중지할 수 있으며, 호스트/코호스트가 강제 중지할 수 있습니다.
여러 참가자가 동시에 화면 공유가 가능합니다.

### DB 변경 (V4 마이그레이션)

```sql
ALTER TABLE room_participants
    ADD COLUMN screen_sharing TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN screen_share_started_at DATETIME(3) DEFAULT NULL;
```

### API

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | `/api/rooms/{roomId}/screen-share/start` | 화면 공유 시작 | JOINED 참가자 |
| POST | `/api/rooms/{roomId}/screen-share/stop` | 화면 공유 중지 | 본인 |
| POST | `/api/rooms/{roomId}/screen-share/force-stop?targetUserId=` | 강제 중지 | HOST/CO_HOST |
| GET | `/api/rooms/{roomId}/screen-share/active` | 공유 중인 참가자 목록 | 누구나 |

### 처리 흐름

```
[화면 공유 시작]
Client ──► POST /screen-share/start
              │
              ├── 방 ACTIVE 상태 확인
              ├── screenShareAllowed 설정 확인
              ├── 참가자 JOINED 상태 확인
              ├── 이미 공유 중인지 확인 (중복 방지)
              ├── participant.startScreenShare() → DB 업데이트
              ├── DataChannel로 SCREEN_SHARE_START 메시지 브로드캐스트
              └── Kafka 이벤트 발행 (SCREEN_SHARE_STARTED)

[강제 중지]
Host ──► POST /screen-share/force-stop?targetUserId=200
              │
              ├── HOST/CO_HOST 권한 검증
              ├── 대상 참가자가 공유 중인지 확인
              ├── participant.stopScreenShare() → DB 업데이트
              ├── LiveKit API로 트랙 뮤트
              ├── DataChannel로 SCREEN_SHARE_STOP 메시지 브로드캐스트
              └── Kafka 이벤트 발행 (SCREEN_SHARE_STOPPED)

[LiveKit Webhook 자동 동기화]
LiveKit ──► POST /webhook/livekit (track_published, source=SCREEN_SHARE)
              │
              └── participant.startScreenShare() → DB 상태 자동 동기화

LiveKit ──► POST /webhook/livekit (track_unpublished, source=SCREEN_SHARE)
              │
              └── participant.stopScreenShare() → DB 상태 자동 동기화
```

---

## 6. 요약

| 서비스 | 상태 | 완료 | 미완료 | 우선순위 |
|--------|------|------|--------|----------|
| User Service | ✅ 완료 | 8/8 | 0 | - |
| Team Service | ✅ 완료 | 4/4 | 0 | - |
| Notification Service | ⚠️ 부분 완료 | 8/15 | 7 | 높음 |
| Minutes Service | ❌ 미구현 | 0/2 | 2 | 중간 |
| Screen Share | ✅ 완료 | - | - | - |

### 남은 작업 우선순위

1. **[P0] `MeetingEventPublisher` Kafka 구현체 작성** — `NoOpMeetingEventPublisher`를 대체하여 회의 시작/종료/참가/강퇴 이벤트를 `notification.send` 토픽으로 발행
2. **[P0] `video-service/docker-compose.yml` Kafka 설정 추가** — `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `depends_on: kafka`
3. **[P1] 스케줄 관련 알림 구현** — `schedule()`, `updateSchedule()`, `cancelSchedule()`에 `NotificationEventPublisher` 연동
4. **[P2] Minutes Service 연동** — `MeetingRoomService.end()`, `RoomRecordingService.handleEgressEnded()`에서 ai-service로 데이터 전달
