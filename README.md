# ONMEET Backend Skeleton

B2B 영상회의 + 회의록(AI 요약) + 알림(SSE) 서비스 ONMEET 백엔드 스켈레톤입니다.

## Tech Stack
- Java 21, Spring Boot 3.x, Gradle
- Spring Web, Validation, Data JPA, Security, Actuator, Lombok
- MySQL 9.0 , Flyway, Testcontainers
- PostgreSQL (local 추천), H2 (test only)
- springdoc OpenAPI/Swagger
- MapStruct

## Local Development Setup

This project uses **Environment Variables** for configuration management.
The `application.yml` files are committed with sensible defaults for local development (using `localhost` and default ports).

### Running with Docker Compose (Recommended)
Docker Compose is already configured to inject the necessary environment variables.

**모든 서비스 실행:**
```bash
docker compose up -d
```

**특정 서비스만 실행 (권장):**
전체 시스템을 올리지 않고 특정 서비스와 그 서비스에 필요한 인프라(DB, Redis, Kafka 등)만 선별해서 실행할 수 있습니다. 반드시 **프로젝트 루트 디렉토리**에서 실행해야 합니다.

```bash
# Auth Service와 관련 필수 인프라만 실행
docker compose up -d auth-service

# 특정 서비스들 조합해서 실행 (예: 인증 + 게이트웨이)
docker compose up -d auth-service gateway-service

# 서비스 재빌드 및 적용
docker compose up -d auth-service --build
```
> **참고**: 개별 서비스 디렉토리(`auth-service/` 등) 내의 `docker-compose.yml`을 직접 실행하면 의존성 문제로 에러가 발생할 수 있으므로, 항상 루트에서 서비스명을 지정하여 실행하는 것을 권장합니다.

### Running Locally (IntelliJ / Gradle)
By default, services are configured to connect to databases on `localhost` with:
- Username: `root`
- Password: `root`

If your local environment differs, setting environment variables in your IDE or shell will override these defaults:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## Profiles
- `local`: PostgreSQL
- `test`: H2 (기본), 일부 테스트는 Testcontainers PostgreSQL 사용

## Endpoints (요약)
- `GET /actuator/health`
- `GET /api-docs`
- `GET /swagger-ui`

### User
- `POST /api/v1/users`
- `GET /api/v1/users/{userId}`
- `GET /api/v1/users`

### Team
- `POST /api/v1/teams`
- `GET /api/v1/teams/{teamId}`
- `GET /api/v1/teams?keyword=`

### Meeting
- `POST /api/v1/meetings`
- `GET /api/v1/meetings/{meetingId}`
- `GET /api/v1/meetings?teamId=`

### Minutes
- `POST /api/v1/minutes`
- `GET /api/v1/minutes/meeting/{meetingId}`

### Notification
- `POST /api/v1/notifications`
- `GET /api/v1/notifications/stream` (Header: `X-User-Id`)

## Notes
- JWT/보안은 뼈대만 포함되어 있습니다. (`JwtAuthenticationFilter`, `JwtTokenProvider`)
- 알림은 InMemory SSE 기반이며, `NotificationPublisher`로 확장 가능하도록 분리되어 있습니다.
- Querydsl 예시는 `TeamRepositoryCustom` + `TeamRepositoryImpl`로 제공됩니다.
