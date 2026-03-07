# Changelog

All notable changes to the Onmeet Backend project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-03-01

### Added - Initial MVP Release

#### Core Services
- **Auth Service** (Kotlin)
  - User authentication and authorization
  - JWT-based authentication with JWKS endpoint
  - Company and team management
  - Role-based access control (RBAC)
  - Profile management with image upload
  - Redis session management
  - Team invitation and approval workflow

- **Gateway Service** (Kotlin)
  - API Gateway with Spring Cloud Gateway (WebFlux)
  - JWT validation and user context forwarding
  - Service routing and load balancing
  - Inter-service authentication via X-Gateway-Secret

- **AI Service** (Java)
  - Meeting summarization with AI
  - STT (Speech-to-Text) integration
  - LLM-based content generation
  - Meeting transcript processing

- **Video Service** (Java)
  - Video conferencing functionality
  - WebRTC support
  - Meeting room management
  - Real-time video/audio streaming

- **Chat Service** (Java)
  - Real-time chat with WebSocket
  - Message persistence
  - Chat room management
  - User presence tracking

- **Notification Service** (Java)
  - Server-Sent Events (SSE)
  - Real-time notification delivery
  - Event subscription management

- **File Service** (Go)
  - File upload/download
  - S3 integration with CloudFront CDN
  - MinIO local storage support
  - Profile image management
  - GORM-based PostgreSQL integration

- **Email Service** (Java)
  - AWS SES integration
  - Email template management
  - Transactional email delivery

#### Shared Libraries
- **onmeet-common** (Kotlin) - Common utilities and DTOs
- **common-security** (Kotlin) - Security configurations and JWT handling

#### Infrastructure
- Kafka 7.5.0 for event-driven communication
- MySQL 9.0 for relational data
- PostgreSQL 16 for file metadata
- Redis for session and caching
- MinIO for local file storage (development)

#### DevOps & CI/CD
- GitHub Actions CI/CD pipeline
  - Selective service building based on git diff
  - Jib for containerless Docker builds (Kotlin/Java)
  - Multi-platform Docker builds (amd64, arm64)
  - Automatic deployment to GCP
- Docker Compose orchestration
- Flyway database migrations
- Health check endpoints

#### Documentation
- API documentation for all services
- Usage guides and API references
- Git workflow documentation
- Release checklist and deployment guides
- CI/CD setup guide

### Technical Stack
- **Languages**: Kotlin 1.9.25, Java 17, Go 1.24
- **Frameworks**: Spring Boot 3.3.5, Gin (Go)
- **Database**: MySQL 9.0, PostgreSQL 16, Redis
- **Messaging**: Kafka 7.5.0
- **Cloud**: AWS (S3, CloudFront, SES), GCP (Compute Engine)
- **Testing**: JUnit 5, MockK, Mockito, Testcontainers

### Architecture
- Microservices Architecture (MSA)
- Database-per-service pattern
- Event-driven communication
- API Gateway pattern
- CQRS for selected services

### Security
- JWT authentication with RS256
- Cookie-based auth (HttpOnly, Secure)
- Inter-service authentication
- Role-based access control
- AES encryption for sensitive data

---

## Release Notes

### v0.1.0 - Initial MVP

이번 릴리즈는 Onmeet 백엔드의 첫 MVP 버전입니다.

**주요 기능**:
- ✅ 사용자 인증 및 권한 관리
- ✅ 화상회의 기능
- ✅ 실시간 채팅
- ✅ AI 회의록 요약
- ✅ 파일 업로드/다운로드
- ✅ 알림 시스템
- ✅ 이메일 발송

**기술 스택**:
- 마이크로서비스 아키텍처 (8개 서비스)
- Kotlin/Java/Go 하이브리드 스택
- Kafka 기반 이벤트 드리븐 아키텍처
- Docker 컨테이너화
- GitHub Actions CI/CD

**배포 환경**:
- GCP Compute Engine
- Docker Compose 오케스트레이션
- 자동 배포 파이프라인

**알려진 제한사항**:
- 프로덕션 환경 최적화 진행 중
- 모니터링 대시보드 구축 예정
- 부하 테스트 미완료

**다음 릴리즈 계획** (v0.2.0):
- [ ] 성능 모니터링 대시보드
- [ ] 로그 집계 시스템
- [ ] 부하 테스트 및 성능 최적화
- [ ] API 문서 자동화 (Swagger)
- [ ] 추가 보안 강화

---

[Unreleased]: https://github.com/evencoding/onmeet-backend/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/evencoding/onmeet-backend/releases/tag/v0.1.0
