# Video Service - 타 서비스 연동 가이드

Video Service 내에서 타 서비스(User, Team, Notification, Minutes)의 연동이 필요한 지점을 정리한 문서입니다.
코드에 `// TODO:` 주석으로 마킹되어 있으며, 각 서비스 담당자가 참고하여 구현하면 됩니다.

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
      │                                │
      │                          Minutes Service
      │                         (회의록/STT 처리)
      │
      └── WebRTC ──► LiveKit Server
```

---

## 1. User Service 연동

### 왜 필요한가?
Video Service는 `userId(Long)`만 갖고 있고 사용자 이름, 프로필 등의 정보가 없습니다.
LiveKit 토큰 발급, 채팅 메시지, 타임라인 등에서 실제 사용자 이름이 필요합니다.

### 필요한 API (Video → User 호출)

| 용도 | 요청 | 응답 |
|------|------|------|
| 단일 사용자 조회 | `GET /api/users/{userId}` | `{ userId, name, profileImageUrl }` |
| 다수 사용자 일괄 조회 | `GET /api/users?ids=1,2,3` | `[{ userId, name, profileImageUrl }, ...]` |
| 사용자 존재 여부 확인 | `GET /api/users/{userId}/exists` | `{ exists: true/false }` |

### 연동 위치 (코드 내 TODO)

```
MeetingRoomService.java
├── join()              → 토큰 생성 시 "user-" + userId 대신 실제 이름 필요
└── getTimeline()       → "User 123 joined as HOST" 대신 실제 이름 표시

RoomParticipantService.java
├── admitWaiting()      → 대기실 승인 시 토큰에 실제 이름 필요
└── admitAllWaiting()   → 일괄 승인 시 각 참가자 이름 필요

ChatIntegrationService.java
└── sendMessage()       → 채팅 메시지 senderName에 실제 이름 필요

ScreenShareService.java
└── publishDataChannelMessage() → DataChannel senderName에 실제 이름 필요

RoomInvitationService.java
├── invite()            → 초대 대상 사용자 존재 여부 검증
└── inviteBulk()        → 다수 사용자 존재 여부 일괄 검증
```

### 연동 예시

```java
// 현재 코드 (placeholder)
liveKitClient.generateToken(roomName, String.valueOf(userId), "user-" + userId, grants);

// 연동 후
UserInfo user = userServiceClient.getUser(userId);
liveKitClient.generateToken(roomName, String.valueOf(userId), user.getName(), grants);
```

---

## 2. Team Service 연동

### 왜 필요한가?
회의방에 `accessScope = TEAM` 설정이 있으면 해당 팀 소속 회원만 입장 가능해야 합니다.
현재는 `teamId`가 null인지만 검증하고, 실제 팀 존재 여부나 멤버십은 확인하지 않습니다.

### 필요한 API (Video → Auth/Team 호출)

| 용도 | 요청 | 응답 |
|------|------|------|
| 팀 존재 확인 | `GET /api/teams/{teamId}` | `{ teamId, name }` |
| 팀 멤버십 확인 | `GET /api/teams/{teamId}/members/{userId}/exists` | `{ isMember: true/false }` |

### 연동 위치 (코드 내 TODO)

```
MeetingRoomService.java
├── create()                → TEAM 방 생성 시 생성자의 팀 멤버십 검증
├── join()                  → TEAM 방 참가 시 참가자의 팀 멤버십 검증
└── validateAccessScope()   → 팀 존재 여부 및 멤버십 확인
```

### 연동 흐름

```
[방 생성 요청 accessScope=TEAM, teamId=5]
      │
      ├── 1. teamId=5 팀이 존재하는가? ──► Team Service
      ├── 2. 요청자가 팀 5의 멤버인가? ──► Team Service
      └── 3. 검증 통과 → 방 생성

[방 참가 요청]
      │
      ├── 1. 방의 accessScope가 TEAM인가?
      ├── 2. 참가자가 해당 팀의 멤버인가? ──► Team Service
      └── 3. 검증 통과 → 참가 허용
```

---

## 3. Notification Service 연동

### 왜 필요한가?
회의 시작/종료, 초대, 강퇴, 대기실 승인 등 주요 이벤트 발생 시 관련 사용자에게 알림을 보내야 합니다.

### 필요한 API (Video → Notification 호출 또는 Kafka 이벤트)

Kafka 토픽 `meeting-events`를 통해 이벤트를 발행하면 Notification Service가 consume하는 구조를 권장합니다.

| 이벤트 | 알림 대상 | 알림 내용 |
|--------|-----------|-----------|
| `MEETING_STARTED` | 초대된 참가자 | "회의가 시작되었습니다" |
| `MEETING_ENDED` | 참가했던 참가자 | "회의가 종료되었습니다" |
| `MEETING_REMINDER` | 초대된 참가자 | "예약된 회의가 곧 시작됩니다" |
| `PARTICIPANT_JOINED` | 호스트/코호스트 | "OOO님이 입장했습니다" |
| `PARTICIPANT_KICKED` | 강퇴된 참가자 | "회의에서 퇴장되었습니다" |
| `WAITING_ADMITTED` | 승인된 참가자 | "입장이 허용되었습니다" |
| `WAITING_REJECTED` | 거절된 참가자 | "입장이 거절되었습니다" |
| `INVITATION_SENT` | 초대받은 사용자 | "OOO님이 회의에 초대했습니다" |
| `INVITATION_ACCEPTED` | 호스트 | "OOO님이 초대를 수락했습니다" |
| `INVITATION_DECLINED` | 호스트 | "OOO님이 초대를 거절했습니다" |
| `INVITATION_CANCELLED` | 초대 취소된 사용자 | "초대가 취소되었습니다" |
| `SCHEDULE_UPDATED` | 초대된 참가자 | "회의 일정이 변경되었습니다" |
| `SCHEDULE_CANCELLED` | 초대된 참가자 | "예약된 회의가 취소되었습니다" |

### 연동 위치 (코드 내 TODO)

```
MeetingRoomService.java
├── start()             → 회의 시작 알림
├── end()               → 회의 종료 알림
├── join()              → 새 참가자 입장 알림 (호스트에게)
├── schedule()          → 예약 회의 생성 알림
├── updateSchedule()    → 일정 변경 알림
├── cancelSchedule()    → 일정 취소 알림
└── sendReminder()      → 리마인더 알림

RoomParticipantService.java
├── kick()              → 강퇴 알림 (대상자에게)
├── admitWaiting()      → 대기실 승인 알림
└── rejectWaiting()     → 대기실 거절 알림

RoomInvitationService.java
├── invite()            → 초대 알림
├── inviteBulk()        → 일괄 초대 알림
├── accept()            → 초대 수락 알림 (호스트에게)
├── decline()           → 초대 거절 알림 (호스트에게)
└── cancelInvitation()  → 초대 취소 알림
```

### Kafka 이벤트 페이로드 예시

```json
{
  "type": "INVITATION_SENT",
  "roomId": 1,
  "roomTitle": "주간 스탠드업",
  "fromUserId": 100,
  "toUserId": 200,
  "timestamp": "2026-03-01T10:00:00Z"
}
```

---

## 4. Minutes Service (회의록) 연동

### 왜 필요한가?
회의 종료 시 녹음 파일과 메타데이터를 회의록 서비스에 전달하여 STT 변환 및 회의록 자동 생성이 가능하도록 해야 합니다.

### 필요한 데이터

| 시점 | 전달 데이터 |
|------|-------------|
| 회의 종료 시 | roomId, 참가자 목록, 시작/종료 시간, 녹음 파일 경로 |
| FULL_AUDIO 녹음 완료 시 | roomId, S3 경로, 파일 크기 (전체 오디오 → STT 용) |
| SEGMENT 녹음 완료 시 | roomId, S3 경로, segmentIndex (실시간 전사 용) |

### 연동 위치 (코드 내 TODO)

```
MeetingRoomService.java
└── end()                  → 회의 메타데이터 전달 (참가자, 시간, 녹음 경로)

RoomRecordingService.java
└── handleEgressEnded()    → 녹음 완료 시 오디오 파일 경로 전달
```

### 연동 흐름

```
[회의 종료]
      │
      ├── 1. 모든 참가자 퇴장 처리
      ├── 2. MEETING_ENDED 이벤트 발행
      └── 3. Minutes Service에 회의 정보 전달 ──► Minutes Service
                                                       │
                                                       └── 회의록 생성 시작

[녹음 완료 (Egress)]
      │
      ├── 1. LiveKit Webhook: egress_ended
      ├── 2. DB에 S3 경로, 파일 크기 저장
      └── 3. Minutes Service에 오디오 전달 ──► Minutes Service
                                                       │
                                                       └── STT 변환 시작
```

### Kafka 이벤트 페이로드 예시

```json
// 회의 종료
{
  "type": "MEETING_ENDED",
  "roomId": 1,
  "hostUserId": 100,
  "participantCount": 5,
  "startedAt": "2026-03-01T10:00:00Z",
  "endedAt": "2026-03-01T11:30:00Z"
}

// 녹음 완료
{
  "type": "AUDIO_SEGMENT_READY",
  "roomId": 1,
  "segmentIndex": 15,
  "s3Path": "/segments/1/segment_015.ogg",
  "startTime": "00:14:00",
  "endTime": "00:15:00"
}
```

---

## 5. 화면 공유 (Screen Share) - 신규 기능

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

### 요청/응답 예시

```
POST /api/rooms/1/screen-share/start
X-User-Id: 100

Response 200:
{
  "participantId": 42,
  "userId": 100,
  "startedAt": "2026-03-01T10:30:00Z"
}
```

```
GET /api/rooms/1/screen-share/active

Response 200:
[
  { "participantId": 42, "userId": 100, "startedAt": "2026-03-01T10:30:00Z" },
  { "participantId": 55, "userId": 200, "startedAt": "2026-03-01T10:32:00Z" }
]
```

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

## 6. TODO 검색 방법

코드에서 각 서비스별 TODO를 찾으려면:

```bash
# 전체 TODO 목록
grep -rn "// TODO:" video-service/src/main/java/com/onmeet/video/meeting/

# 서비스별 필터링
grep -rn "// TODO: \[User Service\]" video-service/src/main/java/com/onmeet/video/meeting/
grep -rn "// TODO: \[Team Service\]" video-service/src/main/java/com/onmeet/video/meeting/
grep -rn "// TODO: \[Notification Service\]" video-service/src/main/java/com/onmeet/video/meeting/
grep -rn "// TODO: \[Minutes Service\]" video-service/src/main/java/com/onmeet/video/meeting/
```

---

## 7. 요약

| 서비스 | TODO 수 | 우선순위 | 담당 |
|--------|---------|----------|------|
| User Service | 7개 | 높음 (이름 표시 필수) | |
| Team Service | 3개 | 중간 (TEAM 접근 제어) | |
| Notification Service | 13개 | 높음 (사용자 경험) | |
| Minutes Service | 2개 | 중간 (회의록 연동) | |

총 **25개** 연동 포인트가 6개 파일에 마킹되어 있습니다.
