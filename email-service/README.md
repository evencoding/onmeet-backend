# email-service

## 서비스 개요

이메일 발송을 담당하는 stateless 마이크로서비스입니다. 주로 Kafka Consumer로 동작하며, `email-send-topic` 토픽에서 이메일 발송 요청을 수신하여 처리합니다. 데이터베이스를 사용하지 않습니다.

- **Port**: `8087`
- **Context Path**: `/email`

## 기술 스택

| 항목 | 기술 |
|------|------|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.3.5 |
| 이메일 발송 | Gmail SMTP (OAuth2 XOAUTH2 인증) |
| 템플릿 엔진 | Thymeleaf |
| 메시지 브로커 | Apache Kafka |
| 빌드 도구 | Gradle |
| 컨테이너 이미지 빌드 | Jib |
| 코드 생성 | Lombok |
| API 문서 | SpringDoc OpenAPI 3 |
| 모니터링 | Micrometer (Prometheus), Zipkin (비활성) |
| 공통 모듈 | `onmeet-common`, `common-security` |

## 주요 기능

- **Kafka Consumer**: `email-send-topic` 토픽에서 이메일 요청을 수신하여 발송
- **Gmail OAuth2 인증**: Google OAuth2 Refresh Token으로 Access Token을 동적으로 발급하고 55분간 캐싱
- **Thymeleaf HTML 이메일**: 3종 HTML 템플릿 지원 (회사 초대, 게스트 초대, 임시 비밀번호)
- **보안**: templateName 화이트리스트 검증으로 Path Traversal 공격 방지
- **헬스 체크**: `GET /email/v1/health`

## 지원 이메일 템플릿

| templateName | 설명 | 템플릿 변수 |
|---|---|---|
| `company-invitation` | 회사 워크스페이스 초대 | `companyName`, `invitationCode` |
| `guest-invitation` | 화상회의 게스트 초대 | `hostName`, `roomName` |
| `temporary-password` | 임시 비밀번호 발급 | `userName`, `temporaryPassword` |

## 프로젝트 구조

```
email-service/
├── src/main/java/com/onmeet/email/
│   ├── EmailApplication.java          # 애플리케이션 진입점
│   ├── config/
│   │   ├── AppConfig.java             # RestTemplate 빈 설정
│   │   ├── GmailOAuth2Config.java     # Gmail OAuth2 자격증명 설정 (ConfigurationProperties)
│   │   ├── GlobalExceptionHandler.java# 전역 예외 처리 (BaseGlobalExceptionHandler 상속)
│   │   └── SecurityConfig.java        # Spring Security 설정 (stateless, /v1/health 허용)
│   ├── controller/
│   │   └── EmailController.java       # REST API 컨트롤러 (헬스 체크)
│   ├── dto/
│   │   └── EmailRequestDto.java       # Kafka 메시지 역직렬화 DTO
│   └── service/
│       ├── EmailService.java          # 이메일 발송 핵심 로직 (템플릿 렌더링, SMTP 발송)
│       ├── EmailKafkaConsumer.java    # Kafka 컨슈머 (email-send-topic)
│       └── GmailOAuth2TokenService.java # Gmail Access Token 발급 및 캐싱
└── src/main/resources/
    ├── application.yml                # 서비스 설정
    └── templates/                     # Thymeleaf HTML 이메일 템플릿
        ├── company-invitation.html
        ├── guest-invitation.html
        └── temporary-password.html
```

## 환경 변수

| 변수명 | 필수 | 기본값 | 설명 |
|--------|------|--------|------|
| `SERVER_PORT` | N | `8087` | 서버 포트 |
| `SPRING_MAIL_HOST` | N | `smtp.gmail.com` | SMTP 호스트 |
| `SPRING_MAIL_PORT` | N | `587` | SMTP 포트 |
| `SPRING_MAIL_USERNAME` | Y | - | Gmail 발신 계정 주소 |
| `GMAIL_CLIENT_ID` | Y | - | Google OAuth2 Client ID |
| `GMAIL_CLIENT_SECRET` | Y | - | Google OAuth2 Client Secret |
| `GMAIL_REFRESH_TOKEN` | Y | - | Google OAuth2 Refresh Token |
| `EMAIL_FROM` | N | `noreply@onmeet.com` | 발신자 이메일 주소 |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | N | `localhost:9092` | Kafka 브로커 주소 |
| `AUTH_INTERNAL_URL` | N | `http://auth-service:8081` | auth-service 내부 URL |

## 빌드 및 실행 방법

개별 서비스 실행 시 .env 파일의 환경변수가 주입되지 않으므로, Docker Compose를 통해 실행한다.

### 빌드

```bash
# Gradle 빌드
./gradlew :email-service:build

# 테스트 제외 빌드
./gradlew :email-service:build -x test

# 테스트 실행
./gradlew :email-service:test

# Docker 이미지 빌드 (Jib)
./gradlew :email-service:jibDockerBuild
```

### 실행 (Docker Compose)

```bash
# 전체 서비스 시작
docker compose up -d

# email-service만 시작 (인프라 포함)
docker compose up -d email-service
```

## Kafka 이벤트 구조

다른 서비스에서 이메일 발송이 필요할 때 `email-send-topic`으로 아래 형식의 JSON 메시지를 발행합니다.

```json
{
  "to": "recipient@example.com",
  "subject": "이메일 제목",
  "templateName": "company-invitation",
  "variables": {
    "companyName": "Acme Corp",
    "invitationCode": "ABC123"
  }
}
```

자세한 내용은 [API_REFERENCE.md](./API_REFERENCE.md)를 참고하세요.

## Gmail OAuth2 설정

이 서비스는 Gmail SMTP에 XOAUTH2 인증 방식을 사용합니다. Google Cloud Console에서 OAuth2 자격증명을 생성하고 Refresh Token을 발급받아 환경 변수로 주입해야 합니다.

Access Token은 만료 경쟁을 방지하기 위해 실제 만료(60분)보다 5분 이른 55분 간격으로 자동 갱신됩니다. 갱신은 double-checked locking 패턴으로 thread-safe하게 처리됩니다.
