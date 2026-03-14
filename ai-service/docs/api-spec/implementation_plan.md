# 구현 계획 — AI Service 설정 통합 및 파이프라인 구축

## 목표

OpenAI STT와 Anthropic Claude 요약을 연결하는 AI 파이프라인을 구축하고, YAML 설정 통합 및 테스트 자동화를 완성한다.

## 구현 완료 내역

### 1. YAML 설정 파일 통합 (6개 파일)

- API 키: `${OPENAI_API_KEY:}`, `${ANTHROPIC_API_KEY:}` 환경변수 참조
- 모델: OpenAI `gpt-4o-mini-transcribe`, Anthropic `claude-sonnet-4-20250514`
- 하드코딩 제거 → Git에 키 노출 방지

### 2. OpenAiSttClient 개선

- WebClient 코덱 메모리 256KB → **30MB** 확장
- `.onStatus()` 에러 응답 body 로깅 추가

### 3. 테스트 코드 구조

| 테스트 클래스 | 위치 | 역할 |
|---|---|---|
| `OpenAiSttIntegrationTest` | `stt/` | 단일 파일 STT + S3 저장 검증 |
| `ChunkedSttIntegrationTest` | `stt/` | 청크 분할 STT (5개 파일) + 개별/병합 결과 저장 |
| `ClaudeSummarizerIntegrationTest` | `nlp/` | Claude 요약 (formal + bullet-point) |
| `E2eAiPipelineIntegrationTest` | `pipeline/` | **전체 파이프라인** (STT → 저장 → 요약 → 저장) |

### 4. 결과 파일 구조

```
src/test/resources/
├── stt_test/                              # STT 관련 테스트 파일
│   ├── assembly_chunk_00~05_10min.m4a     # 분할 오디오 청크
│   ├── assembly_chunk_XX_10min_result.txt  # 개별 청크 STT 결과
│   ├── assembly_chunks_merged_result.txt   # 병합 결과 (구분자 포함)
│   ├── assembly_chunks_merged_plain.txt    # 병합 결과 (순수 텍스트)
│   └── audio_Test_5_result.txt             # whisper-1 전체 변환 결과
├── nlp_test/                              # NLP 관련 테스트 파일
│   ├── assembly_chunks_summary_result.txt  # formal 요약
│   └── assembly_chunks_summary_bullet_result.txt  # bullet-point 요약
└── e2e_test/                              # E2E 통합 테스트 결과
    ├── assembly_chunk_XX_transcript.txt    # 개별 전사 텍스트
    ├── merged_transcript.txt               # 병합 전사 텍스트
    └── meeting_summary.txt                 # 최종 회의 요약
```

## 검증 결과

### STT
- 단일 파일(133KB): ✅ BUILD SUCCESSFUL (42초)
- 청크 분할(5개): ✅ 전체 성공
- 대용량 파일(17MB): `whisper-1` ✅, `gpt-4o-mini-transcribe` ❌

### Claude 요약
- formal 스타일: ✅
- bullet-point 스타일: ✅

### E2E 파이프라인
- Audio → STT → Transcript 저장 → Claude 요약 → Summary 저장: ✅

## 남은 작업

- Kafka 이벤트 기반 파이프라인 연결
- 25MB 초과 파일 분할 처리 로직
- 화자 분리(Speaker Diarization) 외부 서비스 연동
