# 오디오 처리 전략 비교 분석

## 1. 세 가지 방식 개요

### 방식 A: 전체 믹스 (Room Composite)
모든 참가자 음성을 하나로 합친 오디오. 화자 분리가 필요하면 별도 Diarization 사용.

```
LiveKit startRoomCompositeEgress()
    → S3: /recordings/{roomId}/full_audio.ogg (1개)
    → STT + Diarization 1회
```

### 방식 B: 참가자별 (Track Egress, 전체 녹음)
각 참가자 오디오를 별도 파일로 저장. 무음 포함.

```
LiveKit startTrackEgress() × N명
    → S3: /tracks/{roomId}/{participantId}/audio.ogg (N개)
    → STT N회 (무음 포함 전체 길이)
```

### 방식 C: 🏆 참가자별 + 10분 청크 + VAD (확정)
참가자별 오디오를 10분 단위 세그먼트로 저장. ai-service에서 VAD로 무음 스킵 후 STT.

```
LiveKit startTrackEgress(trackSid, s3Path, segmentDuration=600) × N명
    → S3: /tracks/{roomId}/{participantId}/segment_000001.ogg (10분 단위)
    → 세그먼트 완료마다 Webhook → Kafka audio.chunk.ready
    → ai-service: VAD → 발화 구간만 STT
```

> [!NOTE]
> 현행 `startTrackCompositeEgress`는 전체 믹스 세그먼트. 참가자별은 **Track Egress** API 사용.
> LiveKit Track Egress가 `segment_duration` 옵션을 지원하여 10분 단위 자동 분할 가능.

---

## 2. 코드 구조 차이

| 항목 | 전체 믹스 + Diarize | 참가자별 (전체) | 🏆 참가자별 + 10분 청크 + VAD |
|---|---|---|---|
| **Egress 수** | 1~2개 | N개 | N개 (세그먼트 자동 분할) |
| **Egress 관리** | 회의 시작 시 1회 | 참가자 입퇴장마다 | 참가자 입퇴장마다 |
| **S3 파일 수** | 1~2개 | N개 | N × M개 (10분 세그먼트) |
| **화자 식별** | AI 추론 (90~95%) | ✅ 100% | ✅ 100% |
| **STT 모델** | gpt-4o-transcribe-diarize | gpt-4o-mini-transcribe | gpt-4o-mini-transcribe |
| **실시간성** | 세그먼트 시 가능 | 회의 종료 후 | ✅ **10분마다** |
| **video-service 변경** | 최소 | 큼 | 큼 (Track Egress 추가) |

### ai-service 코드 변경 범위

```
전체 믹스 + Diarize:
├── OpenAiSttClient: model → gpt-4o-transcribe-diarize, response_format → diarized_json
├── SttWorkerService: diarized 응답 파싱 로직 추가
├── VoiceSegmentCreatedEvent: 화자별 segment로 분할
└── video-service: 거의 변경 없음 (현행 Room Composite 유지)

참가자별 + VAD:
├── OpenAiSttClient: 현행 유지 (gpt-4o-mini-transcribe)
├── SttWorkerService: 현행 유지 (participantId가 이미 포함)
├── AudioChunkReadyEvent: 현행 유지 (participantId 필수)
└── video-service: Track Egress 관리 + VAD 발화 감지 로직 추가
```

---

## 3. STT + 화자 분리 비용 비교

### 서비스별 가격표

| 서비스 | 모델 | STT | 화자 분리 | 합계 (분당) | 합계 (시간당) |
|---|---|---|---|---|---|
| **OpenAI** | gpt-4o-mini-transcribe | $0.003 | ❌ 미지원 | $0.003 | $0.18 |
| **OpenAI** | gpt-4o-transcribe-diarize | $0.006 (통합) | ✅ 내장 | $0.006 | $0.36 |
| **AssemblyAI** | Universal-2 + diarization | $0.0025 | +$0.00033 | $0.00283 | $0.17 |
| **Deepgram** | Nova-3 (pre-recorded) | $0.0077 (포함) | ✅ 내장 | $0.0077 | $0.46 |

### 회의 중 실제 발화 비율

연구에 따르면, 5인 회의에서 1인당 평균 발화 비율은 **20~30%**:
- 5명 × 60분 × 25% = **75분** (실제 발화 분량, 무음 제거 후)
- 무음 포함 전체: 5명 × 60분 = 300분

### 시나리오: 5명 참가, 1시간 회의

| 방식 | STT 대상 분량 | 모델 (분당) | 화자 식별 | 비용 |
|---|---|---|---|---|
| **참가자별 + VAD** | **75분** | mini-transcribe ($0.003) | ✅ 100% | **$0.23** |
| 전체 믹스 + Diarize (OpenAI) | 60분 | transcribe-diarize ($0.006) | ✅ 90~95% | $0.36 |
| 전체 믹스 + Diarize (AssemblyAI) | 60분 | Universal-2 ($0.00283) | ✅ 90~95% | $0.17 |
| 참가자별 (무음 포함) | 300분 | mini-transcribe ($0.003) | ✅ 100% | $0.90 |
| 세그먼트 + Diarize | 60분 | transcribe-diarize ($0.006) | ✅ 90~95% | $0.36 |
| 세그먼트 (화자분리 X) | 60분 | mini-transcribe ($0.003) | ❌ | $0.18 |

### 월간 비용 추정 (월 200회의 × 1시간 × 5명)

| 방식 | 회의당 | 월간 | 화자 정확도 |
|---|---|---|---|
| **참가자별 + VAD** | **$0.23** | **$46** | **100%** |
| 전체 믹스 + AssemblyAI | $0.17 | $34 | 90~95% |
| 전체 믹스 + OpenAI Diarize | $0.36 | $72 | 90~95% |
| 세그먼트 + OpenAI Diarize | $0.36 | $72 | 90~95% |
| 참가자별 (무음 포함) | $0.90 | $180 | 100% |

---

## 4. 방식별 종합 비교

| 항목 | 전체 믹스 + Diarize | 참가자별 (전체) | 참가자별 + VAD |
|---|---|---|---|
| **비용 (5명/1h)** | $0.36 (OpenAI) / $0.17 (AssemblyAI) | $0.90 | **$0.23** |
| **화자 정확도** | 90~95% (AI 추론) | **100%** (물리적 분리) | **100%** (물리적 분리) |
| **화자 이름 매핑** | Speaker 0 → 매핑 로직 필요 | LiveKit identity **즉시 매핑** | LiveKit identity **즉시 매핑** |
| **동시 발화** | ⚠️ 정확도 떨어짐 | ✅ 문제 없음 | ✅ 문제 없음 |
| **STT 모델** | diarize 전용 ($0.006/분) | mini ($0.003/분) | **mini ($0.003/분)** |
| **STT 대상 분량** | 60분 | 300분 (무음 포함) | **75분** (발화만) |
| **video-service 변경** | **최소** (현행 유지) | 큼 (Track Egress) | 큼 (Track Egress + VAD) |
| **ai-service 변경** | 중간 (diarize 응답 파싱) | 최소 (현행 유지) | **최소** (현행 유지) |
| **LiveKit 지원** | ✅ Room Composite | ✅ Track Egress | ✅ Track Egress + Silero VAD |
| **참조 음성** | 2~10초 샘플로 정확도↑ | 불필요 | 불필요 |
| **실시간성** | 세그먼트 방식이면 가능 | 전체 녹음 후 처리 | VAD 청크 단위 가능 |

---

## 5. 확정 방식: 참가자별 + 10분 청크 + VAD

### 최종 구현 흐름

```
video-service:
  ① 전체 믹스 (FULL_AUDIO) — 현행 유지 (녹음 파일 보관용)
  ② 참가자별 Track Egress (10분 세그먼트) — 추가 구현
     → 참가자 입장 시 startTrackEgress(trackSid, s3Path, 600)
     → 퇴장 시 stopEgress
     → 10분마다 S3에 자동 저장 → Webhook → Kafka audio.chunk.ready

ai-service:
  ③ audio.chunk.ready 수신 → S3에서 세그먼트 읽기
  ④ VAD로 무음 구간 스킵 (오픈소스, 추가 비용 $0)
  ⑤ 발화 구간만 STT 호출 (gpt-4o-mini-transcribe, $0.003/분)
  ⑥ voice.segment.created 발행 (participantId 포함)
```

### 이 방식의 장점

| 항목 | 설명 |
|---|---|
| **비용** | $0.23/회의 — Diarize 대비 36% 저렴, VAD 자체는 무료 |
| **화자 정확도** | 100% — 물리적 분리 |
| **실시간성** | 10분마다 STT 결과 생성 (준실시간) |
| **병렬 처리** | 10분 청크별 동시 STT 가능 |
| **장애 복구** | 실패 시 해당 청크만 재처리 |
| **ai-service 변경** | 최소 — `AudioChunkReadyEvent`의 `participantId`+`chunkSeq` 구조와 완벽 일치 |
| **메모리** | 10분 오디오만 로드 (전체 60분 로드 불필요) |

### 구현 난이도

| 구현 위치 | 해야 할 일 | 난이도 |
|---|---|---|
| video-service | Track Egress 시작/중지 (참가자 입퇴장 연동) | ⭐⭐ |
| video-service | Egress 완료 Webhook → Kafka `audio.chunk.ready` 발행 | ⭐ |
| ai-service | VAD 후처리 (오디오 읽기 → 무음 스킵 → STT) | ⭐ |
| 전체 녹음 | 현행 FULL_AUDIO Egress 유지 | ✅ 이미 구현 |

### S3 저장 구조

```
/recordings/{roomId}/
    └── full_audio.ogg              (전체 믹스, 보관용)

/tracks/{roomId}/{participantId}/
    ├── segment_000001.ogg          (0~10분)
    ├── segment_000002.ogg          (10~20분)
    └── ...

/transcripts/{meetingId}/{transcriptId}.json  (ai-service 생성)
/minutes/{meetingId}/{transcriptId}/summary.json
```

### `AudioChunkReadyEvent` 필드 매핑

```
meetingId      = String.valueOf(roomId)
participantId  = LiveKit identity (userId)
trackId        = trackSid
chunkSeq       = segmentIndex
chunkStartMs   = segmentIndex × 600000
chunkEndMs     = (segmentIndex + 1) × 600000
audioFileKey   = /tracks/{roomId}/{participantId}/segment_{n}.ogg
format         = "ogg"
```

---

## 6. 파일 서비스 S3 중개 — TODO 항목 정리

ai-service에서 S3 직접 접근 → 파일 서비스 경유로 전환할 대상:

| 동작 | 현재 코드 | 파일 서비스 전환 | 위치 |
|---|---|---|---|
| 오디오 읽기 | `storageClient.readBytes(key)` | GET → pre-signed URL / byte[] | `SttWorkerService` |
| 트랜스크립트 저장 | `storageClient.writeText(key, json)` | POST → 업로드 | `TranscriptBuilderService` |
| 트랜스크립트 읽기 | `storageClient.readText(key)` | GET → 다운로드 | `SummaryWorkerService`, `MinutesService` |
| 요약 저장 | `storageClient.writeText(key, json)` | POST → 업로드 | `SummaryWorkerService`, `MinutesService` |
| 파일 삭제 | `storageClient.delete(key)` | DELETE | (미사용) |

### 전환 방식

```
StorageClient (인터페이스) — 변경 없음
    ├── S3StorageClient (현재) — 로컬/테스트용 유지
    └── FileServiceStorageClient (신규) — 파일 서비스 REST API 경유, 프로덕션용
```
