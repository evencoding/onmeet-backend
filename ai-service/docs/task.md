# AI Service 작업 체크리스트

## Phase 1: YAML 설정 통합 및 환경 변수 관리

- [x] `.env` 파일에서 API 키 값 확인 (OPENAI_API_KEY, ANTHROPIC_API_KEY)
- [x] 6개 YAML 파일에 OpenAI/Anthropic/AWS 설정 통합
- [x] API 키를 `${환경변수}` 참조 방식으로 통일 (하드코딩 제거)

## Phase 2: 모델 검증 및 업데이트

- [x] Claude API 키 유효성 검증 (PowerShell로 직접 테스트)
- [x] Anthropic 모델명 `claude-3-5-sonnet-20240620` → `claude-sonnet-4-20250514` 변경
- [x] OpenAI STT 모델명 `gpt-4o-mini-transcribe` 통일

## Phase 3: 테스트 코드 리팩토링

- [x] `OpenAiSttIntegrationTest` — `loadEnv()` 제거, `@SpringBootTest` 전환
- [x] `@TestConfiguration`에서 `WebClient.Builder` Bean 직접 등록

## Phase 4: OpenAiSttClient 개선

- [x] WebClient 코덱 메모리 제한 30MB 확장 (`maxInMemorySize`)
- [x] API 에러 응답 body 로깅 추가 (`.onStatus()` 핸들러)

## Phase 5: 다양한 오디오 파일 테스트

- [x] `stt_Test.m4a` (133KB) — ✅ 성공
- [x] `audio_Test_4.m4a` (3MB, 10분 청크) — ✅ 성공
- [x] `audio_Test_5.m4a` (17MB, 전체) — ❌ `gpt-4o-mini-transcribe` 실패, ✅ `whisper-1` 성공
- [x] `gpt-4o-mini-transcribe` 모델의 실질적 파일 크기 제한 확인 및 문서화

## Phase 6: 청크 분할 STT 테스트

- [x] `ChunkedSttIntegrationTest` 작성
- [x] `assembly_chunk_00~05.m4a` 5개 파일 → `gpt-4o-mini-transcribe` 개별 변환
- [x] 개별 결과 `_result.txt` + 병합 결과 `merged_result.txt` / `merged_plain.txt` 저장
- [x] 테스트 성공 확인

## Phase 7: Claude 요약 테스트

- [x] `ClaudeSummarizerIntegrationTest` 작성
- [x] 병합 전사 텍스트 → Claude 요약 (formal / bullet-point 스타일)
- [x] 요약 결과 `nlp_test/` 폴더에 저장
- [x] `@SpringBootTest(classes={...})`에 `Config.class` 누락 이슈 해결
- [x] 테스트 성공 확인

## Phase 8: E2E 통합 테스트

- [x] `E2eAiPipelineIntegrationTest` 작성
- [x] Audio Chunks → STT → Transcript 저장 → Claude 요약 → Summary 저장 파이프라인
- [x] 결과 파일 `e2e_test/` 폴더 저장 + S3 저장 및 검증
- [x] 테스트 성공 확인

## 미완료

- [ ] Kafka를 통한 이벤트 기반 파이프라인 연결
- [ ] 25MB 초과 파일 분할 처리 로직 구현
- [ ] 화자 분리(Speaker Diarization) 구현 (외부 서비스 연동 필요)
