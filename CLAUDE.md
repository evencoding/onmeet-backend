# CLAUDE.md - Onmeet Backend

## Project Overview
B2B video conferencing platform with AI summarization, built as microservices architecture.

## Services

| Service | Port | Lang | DB | Purpose |
|---------|------|------|----|---------|
| gateway-service | 8080 | Kotlin | - | API Gateway, JWT validation, routing |
| auth-service | 8081 | Kotlin | MySQL + Redis | Auth, users, teams, companies |
| ai-service | 8082 | Java | MySQL | AI summarization (STT/LLM) |
| video-service | 8083 | Java | MySQL | Video conferencing, WebRTC |
| notification-service | 8085 | Java | MySQL | Notifications (SSE) |
| file-service | 8086 | Go | PostgreSQL | File upload/download, S3, profile images |
| email-service | 8087 | Java | - | Email (AWS SES) |

Shared libraries: `onmeet-common`, `common-security` (Kotlin)

## Build & Run Commands

### Gradle (Kotlin/Java services)
```bash
./gradlew build                          # Build all
./gradlew :auth-service:build            # Build specific service
./gradlew build -x test                  # Build without tests
./gradlew test                           # Run all tests
./gradlew :auth-service:test             # Test specific service
./gradlew jibDockerBuild --parallel      # Build Docker images (recommended)
```

### Go (file-service)
```bash
cd file-service
go build -o file-service main.go
go run main.go
swag init                                # Generate Swagger docs
```

### Docker
```bash
docker compose up -d                     # Start all services
docker compose up -d mysql-auth redis-auth kafka zookeeper postgres-file  # Infrastructure only
docker compose down                      # Stop all
```

## Tech Stack
- **Kotlin services**: Spring Boot 3.3.5, Spring Security, JPA, Flyway, Kafka
- **Java services**: Spring Boot 3.3.5, Lombok, MapStruct 1.6.2, Flyway
- **Go service**: Gin, GORM, AWS SDK v2, segmentio/kafka-go
- **Gateway**: Spring Cloud Gateway (reactive/WebFlux)
- **Testing**: JUnit 5, MockK (Kotlin), Mockito (Java), Testcontainers
- **Infra**: Kafka 7.5.0, Redis, MySQL 9.0, PostgreSQL 16

## Conventions
- Package structure: `com.onmeet.<service>/{config,controller,dto,entity,exception,repository,service,util}`
- Go structure: `internal/{client,config,handler,middleware,model,repository,service}`
- Kotlin tests use backtick method names for readability
- Flyway migrations in `src/main/resources/db/migration/`
- Cookie-based JWT auth (HttpOnly, Secure)
- Inter-service auth via `X-Gateway-Secret` header
- Database-per-service pattern
- Event-driven communication via Kafka

## Git Workflow
@GIT_WORKFLOW.md 참조
- **Commit style**: `type(scope): description`
  - 예: `feat(auth-service): Add OAuth2 login`

## Antigravity (Architect) & Claude Code (Developer) Collaboration Guidelines
- **Interactive Review**: 상위 기획자(Antigravity)는 CC에게 작업을 단순히 던지고 끝내는 것이 아니라, PTY나 파이프라인을 통해 상호작용하며 CC의 산출물을 중간 점검합니다.
- **Course Correction**: CC가 잘못된 방향으로 코딩하거나 아키텍처 원칙(MSA, 의존성 격리 등)을 어길 경우, 즉각 개입하여 토론하고 더 나은 설계 방향으로 수정(Refactoring)을 지시합니다.
- **Scope of Intervention**: 보안(Security), 트랜잭션(Transaction), 성능(Performance) 최적화 및 공통 모듈 설계 시 특히 꼼꼼하게 검토(Review)합니다.
