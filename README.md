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
```bash
docker-compose up
```

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
