# Walkthrough — AI Service 설정 통합 및 STT 연동

## 작업 기간
- 이전 세션: `.env` 로드 방식 시도 → 실패 → 순수 JUnit 전환 → Gradle 충돌
- 현재 세션 (2026-02-21): YAML 통합, @SpringBootTest 리팩토링, 에러 핸들링 개선, 다양한 오디오 파일 테스트

---

## 변경된 파일 목록

### 소스 코드

| 파일 | 변경 내용 |
|---|---|
| `OpenAiSttClient.java` | WebClient 버퍼 30MB 설정, 에러 응답 body 로깅 추가 |
| `OpenAiSttIntegrationTest.java` | `@SpringBootTest` 기반 리팩토링, `loadEnv()` 제거 |

### YAML 설정 (6개)

| 파일 | 변경 내용 |
|---|---|
| `main/application.yml` | OpenAI/Anthropic 키를 `${환경변수}` 참조로 설정, Kafka 토픽 설정 |
| `main/application-test.yml` | LocalStack S3 + AWS test credentials + API 키 추가 |
| `main/application-docker.yml` | Docker 서비스 주소 + API 키 추가 |
| `test/application.yml` | main과 동기화 (RabbitMQ → Kafka 전환) |
| `test/application-test.yml` | LocalStack + API 키 + 테스트용 설정 |
| `test/application-docker.yml` | Docker 서비스 주소 + API 키 |

### 공통 변경 사항
- OpenAI 모델: `gpt-4o-mini-transcribe`로 전체 통일
- Anthropic 모델: `claude-sonnet-4-20250514`로 전체 변경
- API 키: 하드코딩 제거, `${OPENAI_API_KEY:}` / `${ANTHROPIC_API_KEY:}` 참조

---

## 테스트 결과

### STT 통합 테스트 (stt_Test.m4a, 133KB)
```
BUILD SUCCESSFUL in 42s
Transcript Result: "안녕하세요. 저는 양진영 입니다."
```
- 음성 → 텍스트 변환: ✅
- LocalStack S3 저장: ✅
- S3 읽기 검증: ✅

### Claude API 키 검증 (PowerShell)
```
SUCCESS: API Key is valid!
Response: Hello! How are you doing today? Is there...
```

### 다양한 오디오 파일 테스트

| 파일 | 크기 | `gpt-4o-mini-transcribe` | `whisper-1` |
|---|---|---|---|
| `stt_Test.m4a` | 133KB | ✅ | ✅ |
| `audio_Test_4.m4a` (10분) | 3MB | ✅ | ✅ |
| `audio_Test_5.m4a` (전체) | 17MB | ❌ 400 Bad Request | ✅ |

---

## 발견된 이슈 및 해결 (총 9건)

> 상세 내용은 [troubleshooting.md](troubleshooting.md) 참조

| # | 이슈 | 해결 |
|---|---|---|
| 1 | `.env` 로드 시점 문제 | YAML `${환경변수}` 참조 + IntelliJ 설정 |
| 2 | Gradle Variant Selection Exception | `@SpringBootTest(classes={...})` 최소 Context |
| 3 | `WebClientAutoConfiguration` import 오류 | `@TestConfiguration`에서 Bean 직접 등록 |
| 4 | Anthropic 모델명 오류 (404) | `claude-sonnet-4-20250514`로 변경 |
| 5 | WebClient 코덱 메모리 제한 | `maxInMemorySize` 30MB 설정 |
| 6 | API 에러 응답 body 미출력 | `.onStatus()` 핸들러 추가 |
| 7 | MIME 타입 비표준 | `audio/mp3` → `audio/mpeg` |
| 8 | OpenAI 파일 크기 제한 | 공식 25MB 제한 문서화 |
| 9 | `gpt-4o-mini-transcribe` 파일 크기 제한 | 실질적으로 `whisper-1`보다 엄격한 제한 확인 |
