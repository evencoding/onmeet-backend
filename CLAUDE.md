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
| chat-service | 8084 | Java | MySQL | Real-time chat (WebSocket) |
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
- Main branch: `develop`
- Feature branches: `feat/ONMEET-<ticket>`
- Commit style: `type(scope): description` (e.g., `feat(auth-service): Add profile management`)
