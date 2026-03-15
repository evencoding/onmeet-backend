# AI Service 트러블슈팅 기록

## 1. Spring Boot Test에서 `.env` 파일 로드 시점 문제

| 항목 | 내용 |
|---|---|
| **증상** | `@BeforeAll`에서 `.env`를 로드하지만, OpenAI API Key가 `null`로 주입됨 |
| **원인** | `@BeforeAll`은 Spring Context가 **이미 로드된 후** 실행됨. `@Value("${openai.api-key}")`는 Context 로딩 시점에 바인딩되므로 `.env`가 너무 늦게 로드됨 |
| **시도 1** | `ApplicationContextInitializer`를 구현하여 Context 로딩 전에 `.env`를 주입 → Gradle 의존성 충돌로 실패 |
| **시도 2** | `@SpringBootTest` 제거, 순수 JUnit + 수동 인스턴스 생성 → Gradle Variant Selection Exception으로 실패 |
| **최종 해결** | YAML 파일에서 `${OPENAI_API_KEY:}` 환경변수 참조 방식 사용 + IntelliJ Run Configuration에 `.env` 경로 지정 |

---

## 2. Gradle Variant Selection Exception (의존성 충돌)

| 항목 | 내용 |
|---|---|
| **증상** | `./gradlew test` 실행 시 `NoCompatibleVariantsFailure` 에러 발생 |
| **원인** | `@SpringBootTest` 사용 시 Spring Boot AutoConfiguration이 전체 Context를 로딩하면서 호환되지 않는 의존성 조합이 발생 |
| **해결** | `@SpringBootTest(classes = {...})`에 필요한 클래스만 명시적으로 지정하여 최소한의 Context만 로딩 |

---

## 3. `WebClientAutoConfiguration` import 오류

| 항목 | 내용 |
|---|---|
| **증상** | 테스트 컴파일 시 `WebClientAutoConfiguration` 클래스를 찾을 수 없음 |
| **원인** | `@Import`에 `WebClientAutoConfiguration.class`를 넣었으나, 해당 클래스가 classpath에 없거나 의존성 충돌 |
| **해결** | `@Import(AwsS3Config.class)`만 남기고, `WebClient.Builder`는 `@TestConfiguration`에서 직접 Bean 등록 |

```java
@org.springframework.boot.test.context.TestConfiguration
static class Config {
    @org.springframework.context.annotation.Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```

---

## 4. Anthropic 모델명 오류 (404 Not Found)

| 항목 | 내용 |
|---|---|
| **증상** | Claude API 호출 시 `404 Not Found` 에러 |
| **원인** | YAML에 설정된 `claude-3-5-sonnet-20240620` 모델 ID가 존재하지 않음 |
| **해결** | 모든 YAML 파일의 모델을 `claude-sonnet-4-20250514`로 변경 |

---

## 5. WebClient 코덱 메모리 제한

| 항목 | 내용 |
|---|---|
| **증상** | 대용량 오디오 파일 전송 시 데이터가 잘릴 가능성 |
| **원인** | Spring WebClient의 기본 코덱 메모리 제한이 **256KB** |
| **해결** | `WebClient` 생성 시 `maxInMemorySize`를 30MB로 설정 |

```java
this.webClient = webClientBuilder
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(30 * 1024 * 1024))
        .build();
```

---

## 6. OpenAI API 에러 응답 Body가 로그에 안 나오는 문제

| 항목 | 내용 |
|---|---|
| **증상** | `400 Bad Request` 발생 시 Spring 기본 에러 메시지만 출력되고, OpenAI의 상세 에러 메시지를 확인할 수 없음 |
| **원인** | `WebClient.retrieve()` 기본 동작은 에러 응답 body를 버림 |
| **해결** | `.onStatus()` 핸들러를 추가하여 에러 응답 body를 `System.err`로 출력 |

```java
.retrieve()
.onStatus(status -> status.isError(), response ->
        response.bodyToMono(String.class)
                .flatMap(body -> {
                    System.err.println("OpenAI API Error [" + response.statusCode() + "]: " + body);
                    return Mono.error(new RuntimeException("OpenAI API Error: " + body));
                })
)
```

---

## 7. MIME 타입 비표준 사용

| 항목 | 내용 |
|---|---|
| **증상** | MP3 파일 전송 시 `audio/mp3`로 설정 |
| **원인** | `audio/mp3`는 비표준 MIME 타입 |
| **해결** | `audio/mpeg`(표준 MIME 타입)으로 수정 |

---

## 8. OpenAI 파일 크기 제한

| 항목 | 내용 |
|---|---|
| **참고사항** | OpenAI Transcription API는 모델에 관계없이 공식적으로 **최대 25MB** 파일 크기 제한 |
| **대응** | 25MB 초과 파일은 분할 처리 로직 구현 필요 (미구현) |

---

## 9. `gpt-4o-mini-transcribe` 모델의 실질적 파일 크기 제한

| 항목 | 내용 |
|---|---|
| **증상** | 동일 인코딩의 m4a 파일에서 3MB(10분)는 성공, 17MB(전체)는 `400 Bad Request: Audio file might be corrupted or unsupported` |
| **원인** | `gpt-4o-mini-transcribe` 모델은 공식 문서상 25MB 제한이나, **실질적으로 더 낮은 크기에서도 거부**하는 현상 확인 |
| **검증** | 동일한 17MB 파일을 `whisper-1`으로 전송 시 **정상 성공** (curl로 직접 확인) |
| **결론** | `gpt-4o-mini-transcribe`는 `whisper-1`보다 파일 크기 제한이 엄격함 |
| **대응** | 실 서비스에서는 음성을 청크 단위(30초~5분)로 분할하여 전송하므로 문제없음 |

**테스트 결과 정리:**

| 파일 | 크기 | `whisper-1` | `gpt-4o-mini-transcribe` |
|---|---|---|---|
| `stt_Test.m4a` | 133KB | ✅ | ✅ |
| `audio_Test_4.m4a` (10분 청크) | 3MB | ✅ | ✅ |
| `audio_Test_5.m4a` (전체) | 17MB | ✅ | ❌ |
| `audio_Test_2.mp3` | 21MB | - | ❌ |

---

## 변경된 주요 파일

| 파일 | 변경 내용 |
|---|---|
| `OpenAiSttClient.java` | WebClient 버퍼 30MB 설정, 에러 응답 body 로깅 추가 |
| `OpenAiSttIntegrationTest.java` | `@SpringBootTest` 기반으로 리팩토링, `loadEnv()` 제거 |
| `application*.yml` (6개) | API 키를 `${환경변수}` 참조 방식으로 통일, 모델명 업데이트 |
