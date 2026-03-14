# AI Service Technical Specification & API Reference

**Version**: v1.0.0
**Last Updated**: 2026-03-14
**Status**: Stable (VAD-STT Pipeline & Summary Integrated)

AI 서비스는 OnMeet 플랫폼의 핵심 지능형 모듈로, 실시간 오디오 처리(VAD), 음성 전사(STT), 그리고 문맥 기반의 회의록 요약(LLM) 기능을 제공합니다.

---

## 1. 시스템 아키텍처 개요 (System Architecture)

### 핵심 워크플로우
1. **Audio Ingest**: 참가자별 10분 오디오 청크 업로드 (`gateway` -> `S3`)
2. **VAD Pipeline**: 무음 제거 및 발화 구간 분리 (`ai-service` 내부 ONNX 모델)
3. **STT Processing**: 각 발화 구간별 텍스트 전사 (`OpenAI Whisper`)
4. **Transcript Merging**: 회의 종료 후 참가자별 대화 병합 및 Redis 임시 저장
5. **AI Summarization**: 전체 트랜스크립트 기반 요약 및 액션 아이템 추출 (`Anthropic Claude`)
6. **Delivery**: 웹소켓 및 REST API를 통해 사용자에게 최종 회의록 제공

---

## 2. 데이터 모델 (Data Models)

### Minutes (Entity)
회의록 정보를 저장하는 주 엔진 엔티티입니다.

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | Long | 고유 식별자 (Auto-increment) |
| `roomId` | Long | 해당 회의실 ID |
| `transcriptId` | String | 트랜스크립트 고유 ID (UUID) |
| `transcriptS3Key` | String | S3에 저장된 전체 트랜스크립트 파일 경로 |
| `summaryS3Key` | String | S3에 저장된 요약본 파일 경로 |
| `summaryJson` | LongText | AI가 생성한 구조화된 요약 데이터 (JSON) |
| `userEditedSummaryJson` | LongText | 사용자가 수정한 요약 데이터 |
| `status` | Enum | `GENERATED`, `EDITED_BY_USER`, `FAILED`, `REGENERATING` |
| `lastError` | String | 마지막 발생 오류 메시지 |

---

## 3. REST API 명세 (REST API Spec)

### 회의록 관리 (Minutes API)

#### 1) 회의록 상세 조회
- **Endpoint**: `GET /v1/minutes/{roomId}`
- **Response**: `MinutesResponse` (Status 200)

#### 2) 회의록 부분 수정
- **Endpoint**: `PATCH /v1/minutes/{roomId}`
- **Request Body**: `MinutesPatchRequest` (제목, 요약 등 선택적 수정)

#### 3) 회의록 AI 재생성
- **Endpoint**: `POST /v1/minutes/{roomId}/regenerate`
- **Description**: 기존 트랜스크립트를 사용하여 AI 요약을 다시 수행합니다.

#### 4) 전체 트랜스크립트 Raw 데이터 조회
- **Endpoint**: `GET /v1/minutes/{roomId}/transcript`
- **Output**: Raw JSON String (참가자별 전체 대화 내역)

### 기타 API
- **내 정보 조회**: `GET /v1/me` (Auth 체크 및 서비스 상태 확인용)

---

## 4. Kafka 메시징 명세 (Messaging Spec)

### Subscribe Topics (인입 메시지)
- **`audio.chunk.ready`**: 오디오 청크 업로드 완료 시 수신 (VAD/STT 파이프라인 시작)
- **`meeting.ended`**: 회의 종료 시 수신 (트랜스크립트 최종 병합 및 요약 프로세스 트리거)
- **`chat.events`**: 회의 중 발생한 채팅 메시지 수신 (트랜스크립트에 채팅 내역 병합용)

### Publish Topics (발행 메시지)
- **`voice.segment.created`**: VAD/STT 완료된 실시간 발화 데이터 (웹소켓 실시간 전송용)
  - Fields: `roomId`, `participantIdentity`, `segmentStartMs`, `segmentEndMs`, `text`, `timestamp`
- **`minutes.generated`**: 회의록 요약본 생성 완료 알림
- **`transcript.finalized`**: 전체 트랜스크립트 영구 저장(S3) 완료 알림

---

## 5. VAD & STT 파이프라인 상세

### VAD (Voice Activity Detection)
- **Model**: Silero VAD v4 (ONNX Runtime)
- **Optimization**:
  - **Normalization**: 오디오 데이터 진폭을 [-1.0, 1.0]으로 정규화하여 낮은 볼륨 환경 성능 개선.
  - **Filtering**: OpenAI API 제약 준수를 위해 100ms 미만의 발화 구간은 무시.
- **Config**:
  - `threshold`: 0.1 (정규화 대응 민감도 상향)
  - `min-speech-duration-ms`: 250ms

### AI Engine Integration
- **STT**: OpenAI Whisper-1 (Multi-language 지원)
- **Summary**: Anthropic Claude 3.5 Sonnet (구조화된 요약 기술)
