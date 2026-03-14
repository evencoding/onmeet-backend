# Gateway Service

Onmeet 플랫폼의 API Gateway 서비스입니다. 모든 외부 요청의 진입점으로 JWT 인증, 라우팅, Rate Limiting, 서비스 간 인증을 담당합니다.

## 서비스 개요

- **포트**: 8080
- **역할**: API Gateway (단일 진입점)
- **주요 책임**:
  - JWT 토큰 검증 (JWKS 기반)
  - 요청 라우팅 (7개 upstream 서비스)
  - Rate Limiting (Redis 기반)
  - 서비스 간 인증 (`X-Gateway-Secret`)
  - 사용자 정보 헤더 전파 (`X-User-*`)
  - CORS 처리
  - 전역 예외 핸들링

## 기술 스택

| 항목 | 기술 |
|---|---|
| 언어 | Kotlin 1.9.25 |
| 런타임 | JVM 17, Spring Boot 3.3.5 |
| 게이트웨이 | Spring Cloud Gateway (Reactive) |
| I/O 모델 | WebFlux (Non-blocking, Reactor) |
| 인증 | Spring Security OAuth2 Resource Server + JWT (JOSE) |
| Rate Limiting | Redis (Reactive, 토큰 버킷 알고리즘) |
| API 문서 | springdoc-openapi-webflux-ui 2.2.0 |
| 빌드 | Gradle |

## 주요 기능

### 1. JWT 인증

- `accessToken` HttpOnly 쿠키에서 JWT를 추출 (`CookieServerAuthenticationConverter`)
- auth-service의 JWKS 엔드포인트(`/auth/v1/.well-known/jwks.json`)를 통해 서명 검증
- JWT claim에서 `role` 필드를 권한(authority)으로 파싱
- 공개 엔드포인트는 인증 없이 통과, 나머지는 인증 필수

### 2. 요청 라우팅

8개 라우트로 7개 서비스에 요청을 전달합니다. 상세 내용은 [GATEWAY_ROUTES.md](./GATEWAY_ROUTES.md) 참고.

### 3. 서비스 간 인증 (X-Gateway-Secret)

`SecureInternalFilter`가 HIGHEST_PRECEDENCE로 실행되어 모든 upstream 요청에 `X-Gateway-Secret` 헤더를 주입합니다. 각 downstream 서비스는 이 헤더를 검증하여 Gateway를 통한 요청임을 확인합니다.

### 4. 사용자 정보 헤더 전파 (X-User-*)

`UserHeaderFilter`가 인증된 요청의 JWT에서 사용자 정보를 추출하여 downstream 서비스에 전달합니다.

| 헤더 | 내용 |
|---|---|
| `X-User-Id` | 사용자 ID (JWT claim `userId` 또는 subject) |
| `X-User-Email` | 사용자 이메일 (JWT subject) |
| `X-User-Roles` | 역할 목록, 쉼표 구분 (e.g. `ROLE_USER`) |

### 5. Rate Limiting

Redis 기반 토큰 버킷 알고리즘으로 전역 Rate Limiting을 적용합니다.
- 기본: 10 req/s, 버스트 최대 20 req
- Key 해석: 인증 principal > `X-Forwarded-For` > remoteAddress

### 6. CORS

허용 Origin:
- `http://localhost:8080`, `http://localhost:*`, `http://127.0.0.1:*`
- `https://onmeet.cloud`, `https://api.onmeet.cloud`, `https://*.onmeet.cloud`

허용 Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD
Credentials: 허용 (쿠키 기반 인증 필요)

### 7. 에러 핸들링

`GlobalErrorWebExceptionHandler`가 모든 예외를 구조화된 JSON 응답으로 변환합니다.

```json
{
  "code": "GW-001",
  "status": 401,
  "message": "인증 토큰이 없거나 형식이 잘못되었습니다.",
  "timestamp": 1700000000000
}
```

20개의 에러 코드(`GatewayErrorCode`)가 JWT 오류, 인증/인가 오류, 업스트림 오류, 서버 오류 등을 구분합니다.

## 프로젝트 구조

```
src/main/kotlin/com/onmeet/gateway/
├── GatewayApplication.kt              # 애플리케이션 진입점
├── config/
│   ├── GatewayConfig.kt               # Rate Limiter Key Resolver Bean 정의
│   ├── SecurityConfig.kt              # Spring Security, CORS, JWT, 인가 규칙 설정
│   └── SwaggerConfig.kt               # (미사용, application.yml로 대체됨)
├── dto/
│   └── ErrorResponse.kt               # 에러 응답 DTO
├── exception/
│   ├── GatewayErrorCode.kt            # 20개 에러 코드 enum (HTTP 상태, 메시지 포함)
│   └── GlobalErrorWebExceptionHandler.kt  # 전역 예외 처리 (ReactiveWebExceptionHandler)
├── filter/
│   ├── SecureInternalFilter.kt        # X-Gateway-Secret 헤더 주입 (GlobalFilter)
│   └── UserHeaderFilter.kt            # X-User-* 헤더 주입 (GatewayFilter)
└── security/
    └── CookieServerAuthenticationConverter.kt  # 쿠키에서 JWT 추출
```

## 환경 설정

### 필수 환경변수

| 변수명 | 설명 | 기본값 |
|---|---|---|
| `GATEWAY_SHARED_SECRET` | 서비스 간 인증 시크릿 | (필수) |

### 선택 환경변수 (서비스 URL)

| 변수명 | 기본값 |
|---|---|
| `SERVER_PORT` | `8080` |
| `AUTH_SERVICE_URL` | `http://auth-service:8081` |
| `AI_SERVICE_URL` | `http://ai-service:8082` |
| `VIDEO_SERVICE_URL` | `http://video-service:8083` |
| `VIDEO_SERVICE_WS_URL` | `ws://video-service:8083` |
| `NOTIFICATION_SERVICE_URL` | `http://notification-service:8085` |
| `FILE_SERVICE_URL` | `http://file-service:8086` |
| `EMAIL_SERVICE_URL` | `http://email-service:8087` |

### Redis (Rate Limiting)

Rate Limiting용 Redis는 auth-service와 동일한 Redis 인스턴스(`redis-auth`)를 사용합니다.

| 변수명 | 기본값 |
|---|---|
| `REDIS_HOST` | `redis-auth` |
| `REDIS_PORT` | `6379` |

## 빌드 및 실행

개별 서비스 실행 시 .env 파일의 환경변수가 주입되지 않으므로, Docker Compose를 통해 실행한다.

### 빌드

```bash
# Gradle 빌드
./gradlew :gateway-service:build

# 테스트 제외 빌드
./gradlew :gateway-service:build -x test

# 테스트 실행
./gradlew :gateway-service:test

# Docker 이미지 빌드 (Jib)
./gradlew :gateway-service:jibDockerBuild
```

### 실행 (Docker Compose)

```bash
# 전체 서비스 시작
docker compose up -d

# gateway-service만 시작 (인프라 포함)
docker compose up -d gateway-service
```

## 타임아웃 설정

| 설정 | 값 |
|---|---|
| Connect Timeout | 3,000ms |
| Default Response Timeout | 10s |
| AI Service Response Timeout | 60s |

## 의존성

- **redis-auth**: Rate Limiting 토큰 버킷 저장소
- **auth-service**: JWKS 엔드포인트 (`/auth/v1/.well-known/jwks.json`) 조회

Gateway 자체는 데이터베이스를 사용하지 않는 stateless 서비스입니다.
