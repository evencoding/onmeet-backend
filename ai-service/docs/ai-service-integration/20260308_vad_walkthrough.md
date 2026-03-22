# Walkthrough: ai-service VAD 기능 추가

## 변경 요약

ai-service에 **Silero VAD(Voice Activity Detection)** 기능을 추가하여, 참가자별 오디오 청크에서 **발화 구간만 추출 → 개별 STT 호출 → 채팅과 시간순 교차 보장** 파이프라인을 구현했습니다.

---

## 변경 파일

### 신규 파일 (4개)

| 파일 | 역할 |
|---|---|
| [VadClient.java](file:///c:/Project/OnMeet/onmeet-backend-main/ai-service/src/main/java/com/onmeet/ai/pipeline/vad/VadClient.java) | VAD 인터페이스 + `SpeechSegment` 레코드 |
| [SileroVadClient.java](file:///c:/Project/OnMeet/onmeet-backend-main/ai-service/src/main/java/com/onmeet/ai/pipeline/vad/SileroVadClient.java) | ONNX Runtime으로 Silero VAD 추론 구현 |
| [NoOpVadClient.java](file:///c:/Project/OnMeet/onmeet-backend-main/ai-service/src/main/java/com/onmeet/ai/pipeline/vad/NoOpVadClient.java) | VAD 비활성화 시 전체를 단일 구간으로 반환 |
| [AudioDecoder.java](file:///c:/Project/OnMeet/onmeet-backend-main/ai-service/src/main/java/com/onmeet/ai/pipeline/audio/AudioDecoder.java) | OGG→PCM 16kHz 변환 + 구간별 WAV 인코딩 |

### 수정 파일

| 파일 | 변경 내용 |
|---|---|
| [SttWorkerService.java](file:///c:/Project/OnMeet/onmeet-backend-main/ai-service/src/main/java/com/onmeet/ai/service/SttWorkerService.java) | VAD 파이프라인 삽입 — 발화별 개별 STT + 정확한 타임스탬프 |
| [build.gradle](file:///c:/Project/OnMeet/onmeet-backend-main/ai-service/build.gradle) | ONNX Runtime, Vorbis 의존성 추가 |
| [application.yml](file:///c:/Project/OnMeet/onmeet-backend-main/ai-service/src/main/resources/application.yml) | `app.vad.*` 설정 블록 추가 |
| [SttWorkerServiceTest.java](file:///c:/Project/OnMeet/onmeet-backend-main/ai-service/src/test/java/com/onmeet/ai/service/SttWorkerServiceTest.java) | VAD Mock 주입, 5개 시나리오 테스트 |

### 추가 리소스

| 파일 | 설명 |
|---|---|
| `src/main/resources/models/silero_vad.onnx` | Silero VAD v4 ONNX 모델 (~2MB) |

---

## 파이프라인 흐름

```
AS-IS:  S3 → byte[] → STT 전체 호출 → 1개 이벤트
TO-BE:  S3 → OGG→PCM → VAD(발화 감지) → 발화별 개별 STT → N개 이벤트 (정확한 timestamp)
```

---

## 빌드 검증

```
> Task :ai-service:compileJava
> Task :ai-service:compileTestJava

BUILD SUCCESSFUL in 23s
```

---

## 설정

```yaml
app:
  vad:
    enabled: ${VAD_ENABLED:false}   # true로 설정 시 Silero VAD 활성화
    threshold: 0.5
    min-speech-duration-ms: 250
    min-silence-duration-ms: 300
    speech-pad-ms: 30
```

기본값 `false`이므로 `NoOpVadClient`가 사용되어 기존 동작과 동일합니다.
`VAD_ENABLED=true`로 설정하면 Silero VAD가 활성화됩니다.
