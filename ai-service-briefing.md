# AI Service 개발 브리핑

## 프로젝트 개요

Spring Boot MSA 프로젝트이며, 나는 **AI Service (회의록/요약)** 담당이다.
전체 서비스 구성은 Auth, Video, Chat, AI, Notification, File, Gateway 이다.

---

## 기술 스택

- **Backend**: Spring Boot 3.3.5 (Java 17)
- **DB**: MySQL (ai_db, 독립 DB)
- **메시지 브로커**: Apache Kafka (KRaft 모드)
- **캐시**: Redis (회의 이벤트 버퍼링)
- **파일 저장소**: AWS S3 (개발: LocalStack)
- **STT**: OpenAI gpt-4o-mini-transcribe
- **요약**: Anthropic Claude claude-sonnet-4-20250514
- **HTTP Client**: Spring WebFlux (WebClient)

---

## 전체 아키텍처

```
Video Service                  Chat (video-service 내장)
    │                                │
    ├── Kafka: audio.chunk.ready     ├── Kafka: chat.events
    ├── Kafka: meeting.ended         │
    │                                │
    └──────────┬─────────────────────┘
               │
         AI Service (Spring Boot, :8082)
               │
               ├── SttWorkerService ◄── audio.chunk.ready
               │       └── S3에서 오디오 읽기 → OpenAI STT → voice.segment.created 발행
               │
               ├── TranscriptBuilderService ◄── voice.segment.created, chat.events, meeting.ended
               │       └── Redis에 이벤트 버퍼링 → 회의 종료 시 S3에 트랜스크립트 저장
               │
               ├── SummaryWorkerService ◄── transcript.finalized
               │       └── S3에서 트랜스크립트 읽기 → Claude 요약 → DB 저장 → minutes.generated 발행
               │
               ├── MinutesService (REST API)
               │       └── 회의록 조회/수정/재생성
               │
               ├── MySQL (ai_db)
               ├── Redis (이벤트 버퍼)
               └── S3 (오디오, 트랜스크립트, 요약)
```

AI Service는 Kafka 이벤트를 소비하여 STT → 트랜스크립트 → 요약의 파이프라인을 처리.
비즈니스 로직은 REST API(회의록 관리)로 제공하고, 파이프라인은 이벤트 기반 비동기 처리.

---

## DB 스키마 (MySQL)

### minutes (회의록)

```sql
CREATE TABLE minutes (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    meeting_id          VARCHAR(100) NOT NULL,
    transcript_id       VARCHAR(100),
    transcript_s3_key   VARCHAR(500),
    transcript_version  INT DEFAULT 1,
    summary_s3_key      VARCHAR(500),
    summary_json        TEXT,
    status              ENUM('GENERATED','EDITED_BY_USER','REGENERATING','FAILED') DEFAULT 'GENERATED',
    access_scope        ENUM('PRIVATE','TEAM','PUBLIC') DEFAULT 'PRIVATE',
    generated_at        DATETIME,
    edited_at           DATETIME,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_meeting (meeting_id)
);
```

---

## API 목록

> context-path: `/ai`, 게이트웨이 라우팅: `/ai/v1/**`

### 회의록 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /v1/minutes/{meetingId} | 회의록 조회 |
| POST | /v1/minutes/{meetingId}/regenerate | 회의록 재생성 |
| PATCH | /v1/minutes/{meetingId} | 회의록 수정 (접근 범위, 사용자 편집) |
| GET | /v1/minutes/{meetingId}/transcript | 원본 트랜스크립트 JSON 조회 |

### 서비스 상태

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /v1/me | AI 서비스 상태 및 인증 정보 확인 |

---

## Kafka 이벤트

### AI Service가 소비하는 토픽

| 토픽 | 이벤트 | Producer | 처리 |
|------|--------|----------|------|
| audio.chunk.ready | 오디오 청크 준비 완료 | Video Service | SttWorkerService → STT 변환 |
| chat.events | 채팅 메시지 발생 | Video Service (채팅 통합) | TranscriptBuilderService → Redis 버퍼 |
| voice.segment.created | STT 결과 생성 (내부) | AI Service 자체 | TranscriptBuilderService → Redis 버퍼 |
| meeting.ended | 회의 종료 | Video Service | TranscriptBuilderService → 트랜스크립트 확정 |
| transcript.finalized | 트랜스크립트 확정 (내부) | AI Service 자체 | SummaryWorkerService → 요약 생성 |

### AI Service가 발행하는 토픽

| 토픽 | 이벤트 | Consumer | 시점 |
|------|--------|----------|------|
| voice.segment.created | STT 변환 결과 | AI (자체 소비) | STT 완료 후 |
| transcript.finalized | 트랜스크립트 확정 | AI (자체 소비) | 회의 종료 처리 후 |
| minutes.generated | 회의록 생성 완료 | Notification | 요약 완료 후 |

### 이벤트 페이로드

```json
// audio.chunk.ready (수신)
{
  "meetingId": "123",
  "participantId": "user_001",
  "trackId": "audio_track_01",
  "chunkSeq": 15,
  "chunkStartMs": 840000,
  "chunkEndMs": 900000,
  "audioFileKey": "audio-chunks/123/user_001/15.ogg",
  "format": "ogg",
  "occurredAtEpochMs": 1741312800000
}

// chat.events (수신)
{
  "meetingId": "123",
  "messageId": "msg-uuid",
  "senderId": "user_002",
  "atMs": 45000,
  "seq": 7,
  "content": "다음 슬라이드로 넘어가죠",
  "occurredAtEpochMs": 1741312845000
}

// meeting.ended (수신)
{
  "meetingId": "123",
  "endedAtEpochMs": 1741316400000
}

// minutes.generated (발행)
{
  "meetingId": "123",
  "transcriptId": "txn-uuid",
  "transcriptS3Key": "transcripts/123/txn-uuid.json",
  "generatedAt": "2024-01-15T12:00:00Z"
}
```

---

## 파이프라인 플로우

```
1. 오디오 청크 도착
   audio.chunk.ready → AudioChunkConsumer → SttWorkerService
   └── S3에서 오디오 다운로드 → OpenAI STT 호출 → VoiceSegmentCreatedEvent 발행

2. 이벤트 수집 (Redis 버퍼링)
   voice.segment.created → VoiceSegmentConsumer → TranscriptBuilderService.ingestVoice()
   chat.events → ChatEventsConsumer → TranscriptBuilderService.ingestChat()
   └── Redis에 시간순으로 저장 (key: meeting:{meetingId}:events)

3. 트랜스크립트 확정
   meeting.ended → MeetingEndedConsumer → TranscriptBuilderService.finalizeMeeting()
   └── Redis에서 전체 이벤트 읽기 → S3에 JSON 저장 → transcript.finalized 발행

4. 요약 생성
   transcript.finalized → TranscriptFinalizedConsumer → SummaryWorkerService
   └── S3에서 트랜스크립트 읽기 → Claude 요약 → DB 저장 → minutes.generated 발행
```

---

## S3 저장 구조

```
audio-chunks/{meetingId}/{participantId}/{chunkSeq}.{ext}
    └── 참가자별 오디오 청크 (video-service가 업로드)

transcripts/{meetingId}/{transcriptId}.json
    └── 확정된 트랜스크립트 (음성+채팅 통합, 시간순 정렬)

minutes/{meetingId}/{transcriptId}/summary.json
    └── Claude가 생성한 요약 결과
```

### TranscriptDocument JSON 구조

```json
{
  "meetingId": "123",
  "transcriptId": "txn-uuid",
  "version": 1,
  "events": [
    {
      "type": "VOICE",
      "participantId": "user_001",
      "startMs": 0,
      "endMs": 5000,
      "text": "회의를 시작하겠습니다"
    },
    {
      "type": "CHAT",
      "participantId": "user_002",
      "atMs": 3000,
      "text": "네, 준비됐습니다"
    }
  ],
  "finalizedAt": "2024-01-15T11:30:00Z"
}
```

---

## Redis 활용

| 키 패턴 | 용도 | TTL |
|---------|------|-----|
| meeting:{meetingId}:events | 음성/채팅 이벤트 버퍼 (Sorted Set, 시간순) | 회의 종료 + 확정 후 삭제 |
| meeting:{meetingId}:dedup | 중복 이벤트 방지 (Set) | 회의 종료 후 삭제 |

---

## 외부 API 연동

### OpenAI (STT)

- **모델**: gpt-4o-mini-transcribe
- **용도**: 오디오 청크 → 텍스트 변환
- **호출**: WebClient (WebFlux)
- **설정**: `openai.api-key`, `openai.api-url`

### Anthropic Claude (요약)

- **모델**: claude-sonnet-4-20250514
- **용도**: 트랜스크립트 → 구조화된 요약 생성
- **호출**: WebClient (WebFlux)
- **설정**: `anthropic.api-key`, `anthropic.api-url`

---

## 인프라 (Docker)

AI Service 관련 컨테이너:
- ai-service (Spring Boot)
- mysql-ai (MySQL)
- kafka (KRaft 모드, 단일 브로커)
- redis (이벤트 버퍼)
- localstack (로컬 S3 에뮬레이션)

### 포트

| 포트 | 용도 |
|------|------|
| 8082 | AI Service HTTP |
| 3306 | MySQL (ai_db) |
| 9092 | Kafka (내부) |
| 29092 | Kafka (외부 접근) |
| 6379 | Redis |
| 4566 | LocalStack (S3 에뮬) |

---

## 개발 우선순위

### 1순위 (MVP) — 완료
- Kafka Consumer 구현 (audio.chunk.ready, chat.events, meeting.ended)
- SttWorkerService (OpenAI STT 연동)
- TranscriptBuilderService (Redis 버퍼 → S3 저장)
- SummaryWorkerService (Claude 요약)
- MinutesController REST API
- 공통 모듈 적용 (GatewayPreAuthFilter, GlobalExceptionHandler)

### 2순위 (통합)
- Video Service와 Kafka 연동 실제 테스트
- 이벤트 DTO 필드 통일 (meetingId/roomId, 시간 포맷)
- Notification Service 연동 (minutes.generated → REST 알림)
- S3 버킷 통합 (LiveKit Egress 녹화 + ai-service 트랜스크립트)

### 3순위 (고도화)
- 다국어 STT 지원
- 요약 스타일 커스터마이징 (간결/상세/액션아이템 등)
- 실시간 자막 (스트리밍 STT)
- 회의록 검색/공유
