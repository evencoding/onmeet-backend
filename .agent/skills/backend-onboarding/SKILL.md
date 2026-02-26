---
name: backend-onboarding
description: Essential guidelines, architecture overview, and common commands for the Onmeet Backend project (Spring Boot Microservices).
---

# Onmeet Backend Development Guide & Skills

## Architecture
The `onmeet-backend` is a microservices architecture built with Spring Boot (Java/Kotlin) and Go.
Key services and their primary languages:
- **Kotlin**: `auth-service`, `gateway-service`, `onmeet-common`
- **Java**: `ai-service`, `chat-service`, `email-service`, `notification-service`, `video-service`
- **Go**: `file-service`

Service Responsibilities:
- `gateway-service`: API Gateway (Entry point)
- `auth-service`: Authentication and user management
- `video-service`: WebRTC and media streaming
- `chat-service`: Real-time messaging
- `email-service`: Email notifications
- `file-service`: File uploads and storage
- `notification-service`: System notifications
- `onmeet-common`: Shared DTOs, utilities, and exceptions
- `common-security`: Shared security context and configurations

## Development Workflow
1. **Infrastructure**: Services rely on external infrastructure like Database (MySQL/Redis) and Kafka. Always ensure they are running before testing services locally by executing `docker-compose up -d`.
2. **Building & Containerization**:
   - **Spring Boot (Java/Kotlin)**: We use **Jib** (`com.google.cloud.tools.jib`) to build Docker images without a Docker daemon.
     - Compile only: `./gradlew build -x test`
     - Build Docker image: `./gradlew jibDockerBuild` (for local Docker)
   - **Go (file-service)**: `gradlew` is not used. Standard `go build` or the provided `Dockerfile` should be used to build operations.
     - Compile: `cd file-service && go build -o main ./internal/...`
     - Build Docker image: `cd file-service && docker build -t onmeet/file-service .`
3. **Testing**: Run `./gradlew test` to execute all unit and integration tests. Test-Driven Development (TDD) is highly encouraged for new features.
4. **Code Style**: 
   - **Kotlin Projects** (`auth-service`, `gateway-service`, `onmeet-common`): Use concise, idiomatic Kotlin.
   - **Java Projects** (`ai-service`, `chat-service`, etc.): Follow standard Java conventions.
   - **Go Projects** (`file-service`): Follow standard Go conventions and use `go fmt`.

## Agent Guidelines & Rules
As an AI Agent working on this project:
1. **Language**: All responses, plans, and reports must be in Korean (`모든 응답은 한글로`).
2. **Role Definition**: Act as the **Senior Technical Planner/Architect (기획자 & 설계자)**. Read the requirements and design the technical specification (ERD, API interface, User Flow) before proceeding to implementation.
3. **Shared Workflows**: Use the slash commands defined in the workflows directory to streamline repetitive tasks (e.g. `/build-all`, `/run-tests`, `/docker-compose-up`).
4. **Context**: Leverage past KIs and conversation logs when troubleshooting tests or specific service details (like `auth-service` controller setups).
