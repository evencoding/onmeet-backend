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

## ⚡ 성능 최적화 (Performance Optimization)

### 📊 정량적 개선 지표 (Quantitative Metrics)

| 항목 (Metric) | 이전 (Kotlin/Spring) | 이후 (Go/Gin) | 개선율 (Improvement) |
| :--- | :--- | :--- | :--- |
| **빌드 속도 (Build Time)** | 31.5s | 8.5s (Gradle 캐시 적용 시) | **73% 단축** |
| **이미지 크기 (Image Size)** | ~450MB | **~25MB** | **94% 감소** |
| **메모리 점유 (Memory)** | ~512MB (JVM 최소) | **~20MB** | **96% 감소** |
| **API 응답 (Health Check)** | 10ms ~ 50ms | **39µs ~ 300µs** | **약 100배 이상 빨라짐** |
| **파일 처리 방식** | Blocking/Thread-pool | **Goroutine 기반 비동기** | 동시 처리 용량 대폭 상승 |
| **데이터베이스 (DB)** | MySQL 9.0 | **PostgreSQL 16 (JSONB)** | 유연한 메타데이터 구조 확보 |

- **Gradle 빌드 최적화**: Parallel Build, Configuration Cache 적용 (기본 31.5s -> 8.5s로 **약 73% 개선**)
- **Google Jib 도입**: Dockerfile 없이 변경된 레이어만 효율적으로 빌드하여 이미지 생성 시간을 혁신적으로 단축
- **Go & PostgreSQL 전환**: I/O 집약적인 `file-service`를 Go로 전환하고, PostgreSQL JSONB를 통해 비정형 메타데이터를 효율적으로 관리합니다.
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
최신 빌드 방식(Jib)이 적용되어, **이미지 빌드 후 실행**해야 합니다.

```bash
# 1. Docker 이미지 빌드 (Jib) - 약 30초 소요
./gradlew jibDockerBuild --parallel

# 2. 전체 서비스 실행 (이미지 Pull 없이 로컬 이미지 사용)
docker compose up -d

# 3. 로그 확인
docker compose logs -f auth-service gateway-service
```

#### 부분 실행 (Partial Execution)
특정 서비스만 실행할 때도 **이미지 빌드가 선행**되어야 합니다.

```bash
# 1. 특정 서비스만 빌드 (예: Auth Service)
./gradlew :auth-service:jibDockerBuild

# 2. 필요한 서비스 실행 (의존성 포함 자동 실행)
docker compose up -d gateway-service auth-service chat-service
```

### 로컬에서 실행 (Local / IntelliJ)
IntelliJ에서 개발할 때는 **인프라만 Docker로 띄우고, 서비스는 IDE에서 실행**하는 것이 디버깅에 유리합니다.

**1. 기반 인프라 실행**
데이터베이스, Kafka, Redis 등 필수 인프라를 실행합니다.
```bash
docker compose up -d mysql-auth redis-auth kafka zookeeper mysql-ai mysql-video mysql-chat mysql-notification postgres-file
```

**2. 서비스 실행 (IntelliJ)**
- 프로젝트 창에서 각 서비스의 `Application.kt` (예: `AuthApplication.kt`)를 우클릭하여 **Run 'AuthApplication'**을 선택합니다.
- 환경 변수(`EnvFile` 플러그인 또는 Run Configuration)가 필요한 경우 설정합니다.

**3. (선택) IntelliJ에서 Docker 이미지 빌드**
터미널 명령어 대신 IDE UI를 사용할 수 있습니다.
- 우측 **Gradle** 탭 > **Tasks** > **jib** > **jibDockerBuild** 더블 클릭

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
