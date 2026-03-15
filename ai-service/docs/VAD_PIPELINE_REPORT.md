# VAD(Voice Activity Detection) 파이프라인 기술 리포트

이 문서는 `ai-service`에 추가된 VAD 파이프라인의 개발 배경, 핵심 구현 내용 및 테스트 가이드를 정리한 문서입니다.

---

## 1. 개발 배경 (Motivation)

### 기존 시스템의 한계
- **STT 비용 낭비**: 참가자 한 명당 10분 단위의 오디오 청크를 통째로 OpenAI Whisper API에 전달했습니다. 대화가 없는 무음 구간(70~80%)까지 비용이 발생했습니다.
- **전사 정확도 저하**: 무음 구간이 길어질 경우 STT 엔진이 환각(Hallucination) 현상을 일으키거나 불필요한 텍스트를 생성할 수 있습니다.
- **타임스탬프 부정합**: 10분 통째로 전사할 경우, 채팅 메시지와 실시간으로 섞을 수 있는 정밀한 발화 시점(ms 단위)을 확보하기 어려웠습니다.

### VAD 도입의 목적
- **비용 절감**: 실제 발화가 있는 구간(Segment)만 추출하여 STT를 호출합니다.
- **정밀 전사**: 각 발화 조각별로 실제 시작/종료 시간을 계산하여 채팅 내역과 정확히 매칭합니다.
- **품질 향상**: 무음 및 배경 노이즈를 사전에 제거하여 STT 결과의 신뢰도를 높입니다.

---

## 2. 주요 기능 및 구현 상세 (Core Features)

### 🧩 핵심 컴포넌트

1.  **`SileroVadClient`**:
    *   **역할**: RNN 기반의 고성능 VAD 모델(Silero v4)을 사용하여 512 샘플(32ms) 단위로 음성 유무를 판단합니다.
    *   **특징**: 상태(State) 기반 추론을 통해 문맥을 유지하며, 이전 발화의 끝과 다음 발화의 시작을 매끄럽게 연결합니다.

2.  **`AudioDecoder` (품질 개선의 핵심)**:
    *   **정규화(Normalization)**: **이번 작업의 핵심 해결책**입니다. 원본 오디오의 볼륨이 작을 경우 VAD 모델이 무음으로 오판하는 것을 방지하기 위해, 전체 진폭을 `[-1.0, 1.0]` 범위로 뻥튀기하여 모델 입력 정합성을 맞춥니다.
    *   **포맷 변환**: 다양한 입력(m4a, ogg 등)을 VAD 모델 표준인 16kHz Mono 16-bit PCM으로 변환합니다.

3.  **`SttWorkerService` (파이프라인 관리)**:
    *   **세그먼트 필터링**: VAD가 감지한 조각 중 100ms 미만의 초단기 조각은 노이즈로 간주하고 OpenAI API 에러 방지를 위해 자동 필터링합니다.
    *   **동적 발행**: 감지된 각 발화 조각을 개별적인 `VoiceSegmentCreatedEvent`로 발행하여 실시간 회의록 시스템의 기초를 만듭니다.

---

## 3. 기술적 문제 해결 기록 (Troubleshooting)

| 현상 | 원인 | 해결 방법 |
| :--- | :--- | :--- |
| **VAD 인식률 저하** (0.0005 확률) | 오디오 진폭이 낮아 모델이 배경 노이즈로 오판함 | `AudioDecoder`에 **Max Amplitude 기반 정규화** 로직 추가 |
| **OpenAI API 에러** (audio_too_short) | VAD가 감지한 0.1초 미만 노이즈 조각 전송 실패 | `SttWorkerService`에서 **100ms 미만 세그먼트 스킵** 로직 추가 |
| **VAD Threshold 보수성** | 기본 임계값(0.5)은 정규화된 환경에서 너무 엄격함 | `threshold`를 **0.1**로 조정하여 실제 대화 생략 방지 |

---

## 4. 테스트 가이드 (How to Test)

### ✅ IntelliJ에서 직접 테스트하기

1.  **환경 변수 설정**:
    *   `Run > Edit Configurations` 에서 `RealAudioPipelineE2ETest` 선택.
    *   `OPENAI_API_KEY`, `ANTHROPIC_API_KEY` 환경 변수 추가.

2.  **데이터 준비**:
    *   `ai-service/src/test/resources/audio/assembly_chunk_00_10min.m4a` 위치에 10분 오디오 파일 배치.

3.  **테스트 실행**:
    *   **전체 파이프라인**: `RealAudioPipelineE2ETest.java` 실행 (Kafka, S3 임베디드 환경 포함).
    *   **VAD 단독 검증**: `SileroVadStandaloneTest.java` 실행 (API 호출 없이 콘솔로 VAD 조각만 확인).

### ⚙️ 주요 설정 (application.yml)
```yaml
app:
  vad:
    enabled: true        # VAD 활성화 여부
    threshold: 0.1       # 감지 민감도 (낮을수록 민감함)
    min-speech-duration-ms: 250   # 버릴 발화의 최소 길이
    min-silence-duration-ms: 300  # 발화를 끊을 무음의 최소 길이
```

---

## 5. 최종 결과물
- **정상 작동 확인**: 10분 분량의 실제 회의 녹음 데이터에서 **130개 이상의 발화 구간**을 정확히 추출하고 전사에 성공했습니다.
- **성능**: 10분 오디오 처리에 약 수 초(VAD) + API 통신 시간 소요.
