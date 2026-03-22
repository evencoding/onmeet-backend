# Gateway Service Routes Reference

API Gateway의 라우팅 규칙 및 필터 체인 정보입니다.
Base URL: `http://localhost:8080`

## 라우팅 테이블 (Routing Table)

| Route ID | Path Predicates | Target URI | 필터 | 비고 |
|---|---|---|---|---|
| `auth-service` | `/auth/v1/**` | `http://auth-service:8081` | UserHeaderFilter | - |
| `ai-service` | `/ai/v1/**` | `http://ai-service:8082` | UserHeaderFilter | response-timeout: 60s |
| `video-service` | `/video/v1/**` | `http://video-service:8083` | UserHeaderFilter | - |
| `video-service-ws-chat` | `/ws-chat/**` | `ws://video-service:8083` | PrefixPath=/video | WebSocket 업그레이드 |
| `notification-service` | `/notification/v1/**` | `http://notification-service:8085` | UserHeaderFilter | SSE 포함 |
| `email-service` | `/email/v1/**` | `http://email-service:8087` | UserHeaderFilter | - |
| `file-service` | `/file/v1/**` | `http://file-service:8086` | UserHeaderFilter | - |
| `file-service-public` | `/file/swagger/**`, `/file/actuator/**` | `http://file-service:8086` | UserHeaderFilter | Swagger/Actuator 공개 |

> 모든 Target URI는 환경변수(e.g. `AUTH_SERVICE_URL`, `FILE_SERVICE_URL`)로 오버라이드 가능합니다.

## 필터 체인 (Filter Chain)

요청 처리 순서:

```
[Client Request]
      |
      v
[CookieServerAuthenticationConverter]  -- accessToken 쿠키에서 JWT 추출
      |
      v
[Spring Security (OAuth2 Resource Server)]  -- JWKS 기반 JWT 서명 검증
      |
      v
[SecureInternalFilter (HIGHEST_PRECEDENCE)]  -- X-Gateway-Secret 헤더 주입 (모든 요청)
      |
      v
[UserHeaderFilter (route-level)]  -- X-User-Id, X-User-Email, X-User-Roles 헤더 주입
      |
      v
[RequestRateLimiter (default-filter)]  -- Redis 기반 Rate Limiting 적용
      |
      v
[Upstream Service]
```

### SecureInternalFilter

- 적용 범위: 전체 요청 (Global Filter)
- 우선순위: `Ordered.HIGHEST_PRECEDENCE`
- 역할: 모든 upstream 요청에 `X-Gateway-Secret` 헤더를 자동 주입하여 서비스 간 인증 구현
- 환경변수: `GATEWAY_SHARED_SECRET`

### UserHeaderFilter

- 적용 범위: 각 라우트에 명시적으로 선언된 경우 적용
- 역할: JWT에서 사용자 정보를 추출하여 downstream 헤더로 전달

| 헤더명 | 출처 | 설명 |
|---|---|---|
| `X-User-Id` | JWT claim `userId` (없으면 subject) | 사용자 식별자 |
| `X-User-Email` | JWT subject | 사용자 이메일 |
| `X-User-Roles` | JWT authorities (쉼표 구분) | 역할 목록 (e.g. `ROLE_USER,ROLE_ADMIN`) |

인증되지 않은 요청(공개 엔드포인트)은 해당 헤더 없이 통과됩니다.

## Rate Limiting (전역 기본 필터)

Redis 기반 토큰 버킷 알고리즘을 사용합니다.

| 설정 | 값 |
|---|---|
| replenishRate | 10 token/s |
| burstCapacity | 20 tokens |
| requestedTokens | 1 token/request |
| Key 해석 순서 | 인증된 principal > X-Forwarded-For > remoteAddress |

## 공개 엔드포인트 (인증 불필요)

| 경로 | 설명 |
|---|---|
| `/auth/v1/register/**` | 회원가입 |
| `/auth/v1/login/**` | 로그인 |
| `/auth/v1/invitations/validate` | 초대 코드 검증 |
| `/auth/v1/check` | 이메일 중복 확인 등 |
| `/auth/v1/refresh` | 액세스 토큰 갱신 |
| `/auth/v1/logout` | 로그아웃 |
| `/.well-known/**` | JWKS 공개키 엔드포인트 |
| `/ws-chat`, `/ws-chat/**` | WebSocket 채팅 |
| `/actuator/health`, `/actuator/info` | 헬스체크 |
| `/swagger-ui/**`, `/v3/api-docs/**` 등 | API 문서 |
| `/error` | 에러 핸들러 |

## HTTP 클라이언트 타임아웃

| 설정 | 값 |
|---|---|
| Connect Timeout | 3,000ms |
| Default Response Timeout | 10s |
| AI Service Response Timeout | 60s (route-level override) |

## Swagger UI 통합 문서

Gateway의 Swagger UI(`/swagger-ui.html`)에서 모든 서비스의 API 문서를 통합 조회할 수 있습니다.

| 서비스 | API Docs URL |
|---|---|
| auth-service | `/auth/v1/v3/api-docs` |
| ai-service | `/ai/v1/v3/api-docs` |
| video-service | `/video/v1/v3/api-docs` |
| notification-service | `/notification/v1/v3/api-docs` |
| email-service | `/email/v1/v3/api-docs` |
| file-service (Go) | `/file/swagger/doc.json` |
