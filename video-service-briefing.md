# Video Service 개발 브리핑

## 프로젝트 개요

Spring Boot MSA 프로젝트이며, 나는 **Video Service (영상회의)** 담당이다.
전체 서비스 구성은 Auth, Video, Chat, AI, Notification, Image, Gateway 이다.

---

## 기술 스택

- **Backend**: Spring Boot
- **DB**: MySQL (서비스별 독립 DB, 다중화 없음)
- **미디어 서버**: LiveKit (SFU 방식)
- **메시지 브로커**: Apache Kafka
- **캐시**: Redis (공용)
- **파일 저장소**: S3
- **프론트엔드**: React + livekit-client + @livekit/components-react

---

## 전체 아키텍처

```
Client (React)
      │
      ├── REST API ──► API Gateway ──► Video Service (Spring Boot)
      │                                      │
      │                                      ├── MySQL (video_db)
      │                                      ├── Redis (실시간 상태)
      │                                      ├── LiveKit Server (REST API 통신)
      │                                      └── Kafka (이벤트 발행)
      │
      └── WebRTC ──► LiveKit Server (SFU)
                          │
                          ├── Egress Worker (녹음)
                          ├── Coturn (TURN/STUN)
                          └── Redis (LiveKit 상태)
```

Video Service는 방 관리, 토큰 발급, 웹훅 처리 등 비즈니스 로직만 담당.
실제 영상/음성 스트리밍은 LiveKit Server가 처리.
시그널링도 LiveKit 내장이라 별도 구현 불필요.

---

## DB 스키마 (MySQL)

### meeting_rooms (회의방)

```sql
CREATE TABLE meeting_rooms (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_code           VARCHAR(20) UNIQUE NOT NULL,         -- 초대 코드
    livekit_room_name   VARCHAR(100) UNIQUE NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    host_user_id        BIGINT NOT NULL,
    status              ENUM('WAITING','ACTIVE','ENDED','CANCELLED') DEFAULT 'WAITING',
    type                ENUM('INSTANT','SCHEDULED','RECURRING') DEFAULT 'INSTANT',
    max_participants    INT DEFAULT 10,
    is_locked           BOOLEAN DEFAULT FALSE,
    password            VARCHAR(255),
    scheduled_at        DATETIME,
    started_at          DATETIME,
    ended_at            DATETIME,
    duration_seconds    INT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_host (host_user_id),
    INDEX idx_status (status),
    INDEX idx_scheduled (scheduled_at)
);
```

### meeting_participants (참가자)

```sql
CREATE TABLE meeting_participants (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id             BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    role                ENUM('HOST','CO_HOST','PARTICIPANT','VIEWER') DEFAULT 'PARTICIPANT',
    status              ENUM('JOINED','LEFT','KICKED','DISCONNECTED') DEFAULT 'JOINED',
    joined_at           DATETIME NOT NULL,
    left_at             DATETIME,
    duration_seconds    INT,
    device_type         ENUM('WEB','MOBILE','DESKTOP'),
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_user (room_id, user_id),
    FOREIGN KEY (room_id) REFERENCES meeting_rooms(id)
);
```

### meeting_settings (방 설정)

```sql
CREATE TABLE meeting_settings (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id              BIGINT NOT NULL UNIQUE,
    video_enabled        BOOLEAN DEFAULT TRUE,
    audio_enabled        BOOLEAN DEFAULT TRUE,
    screen_share_allowed BOOLEAN DEFAULT TRUE,
    chat_enabled         BOOLEAN DEFAULT TRUE,
    recording_enabled    BOOLEAN DEFAULT TRUE,
    waiting_room         BOOLEAN DEFAULT FALSE,
    auto_mute_on_join    BOOLEAN DEFAULT TRUE,
    created_at           DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (room_id) REFERENCES meeting_rooms(id)
);
```

### meeting_recordings (녹화 정보)

```sql
CREATE TABLE meeting_recordings (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id             BIGINT NOT NULL,
    egress_id           VARCHAR(100) NOT NULL,
    type                ENUM('FULL_AUDIO','SEGMENT','FULL_VIDEO') DEFAULT 'SEGMENT',
    status              ENUM('RECORDING','PROCESSING','COMPLETED','FAILED') DEFAULT 'RECORDING',
    s3_path             VARCHAR(500),
    file_size_bytes     BIGINT,
    duration_seconds    INT,
    segment_index       INT,
    started_at          DATETIME,
    ended_at            DATETIME,
    error_message       TEXT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_type (room_id, type),
    INDEX idx_egress (egress_id),
    FOREIGN KEY (room_id) REFERENCES meeting_rooms(id)
);
```

### meeting_tags (태그)

```sql
CREATE TABLE meeting_tags (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id             BIGINT NOT NULL,
    tag_name            VARCHAR(50) NOT NULL,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tag (tag_name),
    UNIQUE KEY uk_room_tag (room_id, tag_name),
    FOREIGN KEY (room_id) REFERENCES meeting_rooms(id)
);
```

### meeting_favorites (즐겨찾기)

```sql
CREATE TABLE meeting_favorites (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    room_id             BIGINT NOT NULL,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_room (user_id, room_id),
    FOREIGN KEY (room_id) REFERENCES meeting_rooms(id)
);
```

---

## API 목록

### 회의방 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/rooms | 회의방 생성 |
| GET | /api/rooms | 회의방 목록 조회 (필터/페이징) |
| GET | /api/rooms/{roomId} | 회의방 상세 조회 |
| PATCH | /api/rooms/{roomId} | 회의방 정보 수정 |
| DELETE | /api/rooms/{roomId} | 회의방 삭제 |
| GET | /api/rooms/code/{roomCode} | 초대 코드로 방 조회 |
| POST | /api/rooms/{roomId}/regenerate-code | 초대 코드 재발급 |

### 회의 진행

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/rooms/{roomId}/join | 회의 참가 (LiveKit 토큰 발급) |
| POST | /api/rooms/{roomId}/leave | 회의 퇴장 |
| POST | /api/rooms/{roomId}/start | 회의 시작 (호스트만) |
| POST | /api/rooms/{roomId}/end | 회의 종료 (호스트만) |
| POST | /api/rooms/{roomId}/lock | 방 잠금 |
| POST | /api/rooms/{roomId}/unlock | 방 잠금 해제 |

### 참가자 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/rooms/{roomId}/participants | 현재 참가자 목록 |
| GET | /api/rooms/{roomId}/participants/history | 참가 이력 |
| PATCH | /api/rooms/{roomId}/participants/{userId}/role | 역할 변경 |
| POST | /api/rooms/{roomId}/participants/{userId}/kick | 강제 퇴장 |
| POST | /api/rooms/{roomId}/participants/{userId}/mute | 강제 음소거 |
| POST | /api/rooms/{roomId}/participants/{userId}/unmute | 음소거 해제 |

### 미디어 제어

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/rooms/{roomId}/mute-all | 전체 음소거 |
| POST | /api/rooms/{roomId}/unmute-all | 전체 음소거 해제 |
| POST | /api/rooms/{roomId}/disable-video-all | 전체 비디오 끄기 |
| PATCH | /api/rooms/{roomId}/settings | 방 설정 변경 |
| GET | /api/rooms/{roomId}/settings | 현재 방 설정 조회 |

### 녹화

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/rooms/{roomId}/recording/start | 녹화 시작 |
| POST | /api/rooms/{roomId}/recording/stop | 녹화 중지 |
| GET | /api/rooms/{roomId}/recording/status | 녹화 상태 조회 |
| GET | /api/rooms/{roomId}/recordings | 녹화 파일 목록 |
| GET | /api/recordings/{recordingId}/download | 다운로드 URL |
| DELETE | /api/recordings/{recordingId} | 녹화 삭제 |

### 예약 회의

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/rooms/schedule | 예약 회의 생성 |
| GET | /api/rooms/scheduled | 내 예약 회의 목록 |
| PATCH | /api/rooms/{roomId}/schedule | 예약 시간 변경 |
| DELETE | /api/rooms/{roomId}/schedule | 예약 취소 |
| POST | /api/rooms/{roomId}/schedule/remind | 리마인드 알림 |

### 초대

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/rooms/{roomId}/invite | 유저 초대 |
| POST | /api/rooms/{roomId}/invite/bulk | 다수 유저 일괄 초대 |
| GET | /api/rooms/{roomId}/invitations | 초대 목록 |
| POST | /api/invitations/{invitationId}/accept | 초대 수락 |
| POST | /api/invitations/{invitationId}/decline | 초대 거절 |
| DELETE | /api/rooms/{roomId}/invite/{userId} | 초대 취소 |

### 태그 / 즐겨찾기

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/rooms/{roomId}/tags | 태그 추가 |
| DELETE | /api/rooms/{roomId}/tags/{tagName} | 태그 삭제 |
| GET | /api/rooms/tags/{tagName} | 태그로 검색 |
| POST | /api/rooms/{roomId}/favorite | 즐겨찾기 추가 |
| DELETE | /api/rooms/{roomId}/favorite | 즐겨찾기 해제 |
| GET | /api/rooms/favorites | 내 즐겨찾기 목록 |

### 대기실

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/rooms/{roomId}/waiting | 대기 중인 유저 목록 |
| POST | /api/rooms/{roomId}/waiting/{userId}/admit | 입장 허가 |
| POST | /api/rooms/{roomId}/waiting/{userId}/reject | 입장 거절 |
| POST | /api/rooms/{roomId}/waiting/admit-all | 전체 입장 허가 |

### 통계 / 이력

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/rooms/{roomId}/stats | 회의 통계 |
| GET | /api/rooms/history | 내 회의 이력 |
| GET | /api/rooms/history/monthly | 월별 통계 |
| GET | /api/rooms/{roomId}/timeline | 타임라인 |

### 웹훅 (LiveKit → Video Service)

| Method | Endpoint | 이벤트 | 처리 |
|--------|----------|--------|------|
| POST | /webhook/livekit | room_started | 방 상태 ACTIVE 변경 |
| | | room_finished | 방 상태 ENDED, 녹화 마무리 |
| | | participant_joined | 참가자 DB 기록 |
| | | participant_left | 퇴장 시간 기록 |
| | | track_published | 미디어 상태 업데이트 |
| | | egress_started | 녹화 상태 RECORDING |
| | | egress_ended | 녹화 상태 COMPLETED |

---

## LiveKit 연동

### Video Service가 하는 것
- LiveKit REST API로 Room 생성/삭제
- JWT 토큰 발급 (identity, room, grants 포함)
- Egress 시작/중지 API 호출
- 웹훅 수신 및 처리

### LiveKit 토큰에 담기는 정보
- identity: 사용자 고유 ID
- room: 참가할 방 이름
- grants: canPublish, canSubscribe, canPublishData, roomAdmin
- ttl: 토큰 유효시간

### Video Service가 안 하는 것
- WebRTC 연결 관리 (LiveKit이 처리)
- 미디어 스트리밍 (LiveKit이 처리)
- 시그널링 (LiveKit 내장)

---

## Egress (녹음) 전략

### 구성: Egress 2개 동시 실행

```
회의 시작 시:
├── Egress 1: 전체 오디오 녹화 → S3: /recordings/{roomId}/full.ogg
└── Egress 2: 세그먼트 저장 (30~60초) → S3: /segments/{roomId}/segment_001.ogg ...
```

### 비정상 종료 대비
- 세그먼트 방식이므로 크래시 시에도 이전 세그먼트는 보존됨
- Egress Primary + Backup 이중화 권장
- 10초마다 Egress Health Check
- metadata.json으로 세그먼트 상태 추적
- Primary 누락 시 Backup에서 복구

### S3 저장 구조

```
/recordings/{roomId}/
    └── full_audio.ogg

/segments/{roomId}/
    ├── segment_000001.ogg  (0:00~1:00)
    ├── segment_000002.ogg  (1:00~2:00)
    └── ...

/recordings/{roomId}/metadata.json  (상태 추적용)
```

---

## Kafka 이벤트

### Video Service가 발행하는 토픽

| 토픽 | 이벤트 | Consumer | 시점 |
|------|--------|----------|------|
| meeting-events | MEETING_STARTED | AI, Notification | 첫 참가자 입장 |
| meeting-events | MEETING_ENDED | AI, Notification | 회의 종료 |
| meeting-events | PARTICIPANT_JOINED | Notification | 참가자 입장 |
| meeting-events | PARTICIPANT_LEFT | Notification | 참가자 퇴장 |
| audio-segments | AUDIO_SEGMENT_READY | AI | 세그먼트 S3 저장 완료 |

### 이벤트 페이로드 예시

```json
// meeting-events
{
  "type": "MEETING_ENDED",
  "roomId": "room_abc123",
  "hostUserId": "user_001",
  "participantCount": 5,
  "startedAt": "2024-01-15T10:00:00Z",
  "endedAt": "2024-01-15T11:30:00Z"
}

// audio-segments
{
  "type": "AUDIO_SEGMENT_READY",
  "roomId": "room_abc123",
  "segmentIndex": 15,
  "s3Path": "/segments/room_abc123/segment_015.ogg",
  "startTime": "00:14:00",
  "endTime": "00:15:00"
}
```

---

## Redis 활용

| 키 | 용도 | TTL |
|----|------|-----|
| room:{roomId}:participants | 현재 참가자 Set | 회의 종료 시 삭제 |
| room:{roomId}:status | 방 상태 | 회의 종료 시 삭제 |
| user:{userId}:current_room | 유저가 참여 중인 방 | 퇴장 시 삭제 |
| room:{roomId}:egress_status | Egress 상태 추적 | 회의 종료 시 삭제 |

---

## 개발 우선순위

### 1순위 (MVP)
- 방 생성/조회/삭제 API
- 회의 참가 (LiveKit 토큰 발급)
- 회의 시작/종료
- 참가자 목록 조회
- LiveKit 웹훅 수신
- 녹화 시작/중지

### 2순위 (v1.0)
- 초대 기능
- 참가자 강퇴/음소거
- 방 설정
- 녹화 파일 목록/다운로드
- 회의 이력
- 태그/즐겨찾기
- Kafka 이벤트 발행

### 3순위 (v2.0)
- 예약 회의
- 대기실
- 전체 음소거
- 통계/월별 리포트
- 초대 코드 재발급

---

## 인프라 (Docker)

Video Service 관련 컨테이너:
- video-service (Spring Boot)
- mysql-video (MySQL)
- livekit-server
- livekit-egress (Primary)
- livekit-egress-backup (Backup)
- coturn (TURN/STUN)
- redis (공용)

### 포트

| 포트 | 용도 |
|------|------|
| 7880 | LiveKit HTTP API |
| 7881 | LiveKit WebRTC (TCP) |
| 50000-60000 | WebRTC 미디어 (UDP) |
| 3478 | TURN/STUN |
