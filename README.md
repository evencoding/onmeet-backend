# ONMEET Backend

B2B 화상 회의 + AI 요약 + 알림 서비스 (MSA w/ Spring Boot 3 & Kotlin)

## 🏗 기술 스택 (Tech Stack)

### Core
- **Language**: Kotlin (JDK 21)
- **Framework**: Spring Boot 3.3.5, Spring Cloud 2023.x
- **Build Tool**: Gradle (Kotlin DSL)

### 아키텍처 및 통신 (Architecture & Communication)
- **Architecture**: 마이크로서비스 아키텍처 (MSA)
- **Gateway**: Spring Cloud Gateway
- **Communication**: REST API, gRPC (내부 통신), Kafka (이벤트 기반)
- **Database**: MySQL 9.0 (서비스별 DB 분리)
- **Cache**: Redis (인증, 세션 관리)

### 보안 (Security)
- **Authentication**: JWT (Access/Refresh Token) + Cookie (HttpOnly)
- **Authorization**: 역할 기반 접근 제어 (Admin, User)

---

## 🚀 서비스 목록 (Services)

| 서비스 (Service) | 포트 (Port) | 설명 (Description) | DB |
|---|---|---|---|
| **Gateway Service** | `8080` | 진입점, 라우팅, 필터 (인증 체크) | - |
| **Auth Service** | `8081` | 사용자 인증, 토큰 관리, 팀/기업 관리 | `auth_db`, Redis |
| **AI Service** | `8082` | AI 요약 생성 (STT/LLM) | `ai_db` |
| **Video Service** | `8083` | 화상 회의 관리, WebRTC 시그널링 | `video_db` |
| **Chat Service** | `8084` | 실시간 채팅 (WebSocket/RSocket) | `chat_db` |
| **Notification** | `8085` | 실시간 알림 (SSE) | `notification_db` |
| **File Service** | `8086` | 파일 업로드/다운로드 관리 | `file_db` |
| **Email Service** | `8087` | 이메일 발송 (AWS SES / SMTP) | - |

> **참고**: Gateway 설정 등에서 언급되는 `Image Service`는 `File Service` (8086)와 동일합니다.

---

## 📚 문서 (Documentation)

- **[Auth Service API 명세서](auth-service/API_REFERENCE.md)**: 상세 API 명세
- **[로그인 & 테스트 가이드](auth-service/LOGIN_GUIDE.md)**: 로컬 인증 테스트 방법
- **[공통 모듈(`onmeet-common`) 가이드](onmeet-common/ONMEET_COMMON_GUIDE.md)**: 공통 클라이언트 및 예외 처리 사용법

---

## 🛠 시작하기 (Getting Started)

### 필수 요구사항 (Prerequisites)
- JDK 21 이상
- Docker & Docker Compose

### Docker Compose로 실행 (권장)
루트 경로의 `docker-compose.yml`을 사용하여 전체 시스템 또는 특정 서비스를 실행할 수 있습니다.

```bash
# 1. 기반 인프라 (MySQL, Redis, Kafka, Zookeeper) + 모든 서비스 실행
docker compose up -d

# 2. 로그 확인
docker compose logs -f auth-service gateway-service
```

#### 부분 실행 (Partial Execution)
리소스를 절약하기 위해 필요한 서비스만 선별하여 실행할 수 있습니다. 의존성(MySQL, Redis 등)은 자동으로 함께 실행됩니다.

```bash
# Gateway + Auth Service + Chat Service 실행
docker compose up -d gateway-service auth-service chat-service
```

### 로컬에서 실행 (Local / IntelliJ)
각 서비스는 IntelliJ 또는 Gradle 명령어로 개별 실행 가능합니다.
단, DB나 Redis 같은 인프라는 실행되어 있어야 합니다.

```bash
# 인프라만 먼저 실행
docker compose up -d mysql-auth redis-auth kafka zookeeper
```

---

## ⚙️ 환경 설정 (Configuration)

이 프로젝트는 민감한 정보와 환경별 설정을 위해 **환경 변수(Environment Variables)**를 사용합니다.
`application.yml`의 기본값은 로컬 개발 환경에 맞춰져 있습니다.

### 주요 환경 변수
루트 디렉토리에 `.env` 파일을 생성하여 관리할 수 있습니다. (로컬 개발 시 선택 사항)

```properties
# 전역 설정
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local

# 보안 (Security)
JWT_SECRET_KEY=your-secret-key-must-be-long-enough
AUTH_ENCRYPTION_KEY=encryption-key
GATEWAY_SHARED_SECRET=shared-secret-for-internal-auth

# 데이터베이스 (전역 또는 서비스별)
DB_ROOT_PASSWORD=root
DB_USERNAME=root

# AWS / 외부 서비스
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
```

---

## 🔗 엔드포인트 및 API 명세 (Endpoints & API Reference)

각 서비스의 상세한 API 명세는 아래 링크 및 **Swagger UI**를 참조하세요.

| 서비스 (Service) | 경로 접두사 (Path Prefix) | API 명세서 (Reference) | Swagger UI |
|---|---|---|---|
| **Gateway** | `/` | [GATEWAY_ROUTES.md](gateway-service/GATEWAY_ROUTES.md) | [Link](http://localhost:8080/webjars/swagger-ui/index.html) |
| **Auth** | `/auth/**` | [API_REFERENCE.md](auth-service/API_REFERENCE.md) | [Link](http://localhost:8081/auth/swagger-ui.html) |
| **AI** | `/ai/**` | [API_REFERENCE.md](ai-service/API_REFERENCE.md) | [Link](http://localhost:8082/swagger-ui.html) |
| **Video** | `/video/**` | [API_REFERENCE.md](video-service/API_REFERENCE.md) | [Link](http://localhost:8083/swagger-ui.html) |
| **Chat** | `/chat/**` | [API_REFERENCE.md](chat-service/API_REFERENCE.md) | [Link](http://localhost:8084/swagger-ui.html) |
| **Notification** | `/notification/**` | [API_REFERENCE.md](notification-service/API_REFERENCE.md) | [Link](http://localhost:8085/swagger-ui.html) |
| **File** | `/file/**` | [API_REFERENCE.md](file-service/API_REFERENCE.md) | [Link](http://localhost:8086/swagger-ui.html) |
| **Email** | - | [API_REFERENCE.md](email-service/API_REFERENCE.md) | [Link](http://localhost:8087/swagger-ui.html) |

### 상태 확인 (Health Check)
- `GET /actuator/health` (모든 서비스 공통)
