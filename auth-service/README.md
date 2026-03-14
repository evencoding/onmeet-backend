# Auth Service

## 서비스 개요

`auth-service`는 Onmeet B2B 화상회의 플랫폼의 인증/인가를 담당하는 마이크로서비스입니다.
기업 회원가입, 사원 초대, 로그인, JWT 토큰 발급/갱신/폐기, 팀 관리, 직급 관리, 게스트 인증 등 플랫폼 전반의 인증 기반을 제공합니다.

- **포트**: `8081`
- **Context Path**: `/auth`
- **API 문서**: [API_REFERENCE.md](./API_REFERENCE.md)
- **보안 가이드**: [DISTRIBUTED_SECURITY_GUIDE.md](./DISTRIBUTED_SECURITY_GUIDE.md)
- **로그인 테스트 가이드**: [LOGIN_GUIDE.md](./LOGIN_GUIDE.md)

---

## 기술 스택

| 항목 | 상세 |
|---|---|
| 언어 | Kotlin |
| 프레임워크 | Spring Boot 3.3.5 |
| 보안 | Spring Security 6 |
| 인증 | JWT (RS256, Nimbus JOSE+JWT) |
| ORM | Spring Data JPA + Hibernate |
| 데이터베이스 | MySQL 9.0 |
| 캐시/세션 | Redis (Refresh Token 저장, 토큰 블랙리스트) |
| 마이그레이션 | Flyway |
| 이벤트 | Apache Kafka (알림 및 이메일 이벤트 발행) |
| 외부 연동 | file-service (프로필 이미지, Feign Client + Circuit Breaker) |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |

---

## 주요 기능

### 인증 (Authentication)
- 이메일/비밀번호 기반 로그인 (JWT RS256 발급)
- HttpOnly 쿠키 기반 Access Token + Refresh Token 관리
- Refresh Token Rotation (갱신 시 기존 토큰 폐기)
- 로그아웃 시 Access Token 블랙리스트 처리 (Redis)
- 게스트 로그인 (회원가입 없는 임시 토큰 발급, 4시간 유효)
- JWK Set 공개 엔드포인트 (`/.well-known/jwks.json`) — Gateway의 JWT 검증에 사용

### 회원 관리 (User Management)
- 기업(관리자) 회원가입 — 회사, 관리자 계정, 기본 팀 동시 생성
- 사원 회원가입 — 초대 코드 기반 검증 후 합류
- 프로필 수정 (이름, 사번, 직급, 프로필 이미지)
- 비밀번호 변경 / 비밀번호 찾기 (임시 비밀번호 이메일 발송)
- 회원 탈퇴 (소프트 삭제 + WithdrawnUser 기록)
- 계정 활성화/비활성화 (매니저 전용)

### 팀 관리 (Team Management)
- 팀 생성 요청 — 일반 직원은 PENDING_APPROVAL, 매니저는 즉시 ACTIVE
- 팀 승인/반려 (매니저 전용, 반려 사유 저장)
- 팀장 임명/위임
- 팀 해체

### 직급 관리 (Job Title Management)
- 직급 생성/수정/삭제 (매니저 전용)
- 직급 목록 조회

### 초대 관리 (Invitation Management)
- 멤버 초대 — 이메일 리스트 기반, 최대 100개 동시 초대
- 게스트 초대 — UUID 링크 기반 일회성 게스트 접속

### 서비스 간 권한 검증
- 팀장/팀원/동일회사 여부 내부 API (`X-Internal-Secret` 인증)
- 사용자 정보 조회 내부 API (`X-Gateway-Secret` 인증)

---

## 프로젝트 구조

```
auth-service/src/main/kotlin/com/onmeet/auth/
├── AuthApplication.kt          # 진입점
├── client/
│   └── FileClient.kt           # file-service Feign Client (프로필 이미지)
├── config/
│   ├── AppConfig.kt            # RestTemplate, Feign 등 공통 빈
│   ├── JpaConfig.kt            # JPA 설정 (Auditing 등)
│   ├── JwtProperties.kt        # JWT 설정 프로퍼티 바인딩
│   ├── OpenApiConfig.kt        # Swagger 설정
│   ├── PropertiesConfig.kt     # 기타 프로퍼티
│   └── SecurityConfig.kt       # Spring Security 필터 체인 설정
├── controller/
│   ├── AuthController.kt       # 인증 API (/v1)
│   ├── MemberController.kt     # 회원 API (/v1/member)
│   ├── ManagerController.kt    # 관리자 API (/v1/manager)
│   ├── GuestController.kt      # 게스트 API (/v1/guests)
│   ├── InternalUserController.kt   # 내부 사용자 API (/internal/users)
│   ├── InternalTeamController.kt   # 내부 팀 API (/internal/teams)
│   └── internal/
│       └── InternalSecurityController.kt  # 권한 검증 API (/internal/v1/security)
├── dto/
│   ├── AuthDto.kt              # 인증/사용자/팀/회사 관련 DTO
│   ├── GuestDto.kt             # 게스트 DTO
│   ├── JobTitleDto.kt          # 직급 DTO
│   ├── UserInfoDto.kt          # 내부 서비스용 간소화 사용자 정보
│   ├── UserProfileUpdateRequest.kt
│   ├── ChangePasswordRequest.kt
│   ├── WithdrawRequest.kt
│   ├── InvitationResponse.kt
│   └── DtoExtensions.kt        # Entity → DTO 변환 확장 함수
├── entity/
│   ├── User.kt                 # 사용자 (UserDetails 구현)
│   ├── Company.kt              # 회사
│   ├── Team.kt                 # 팀
│   ├── TeamMember.kt           # 팀-사용자 연결 (역할: LEADER/MEMBER)
│   ├── Invitation.kt           # 멤버 초대
│   ├── GuestInvitation.kt      # 게스트 초대 (UUID 링크)
│   ├── JobTitle.kt             # 직급
│   ├── RefreshToken.kt         # Redis Refresh Token 엔티티
│   ├── ServerKey.kt            # RSA 키 쌍 저장
│   └── WithdrawnUser.kt        # 탈퇴 사용자 기록
├── exception/
│   ├── GlobalExceptionHandler.kt   # BusinessException 기반 전역 에러 핸들러
│   └── *.kt                    # 도메인별 예외 클래스 (AuthErrorCode 기반)
├── repository/
│   ├── jpa/                    # JPA 리포지토리 (9개)
│   └── redis/
│       └── RefreshTokenRepository.kt  # Redis Refresh Token 리포지토리
├── scheduler/
│   └── WithdrawalScheduler.kt  # 탈퇴 사용자 정리 스케줄러
├── security/
│   ├── JwtTokenProvider.kt     # JWT 생성/검증 (RS256)
│   ├── KeyManager.kt           # RSA 키 관리 (AES-256-GCM 암호화 저장)
│   ├── JwtAuthenticationFilter.kt  # JWT 인증 필터
│   ├── AuthGatewayPreAuthFilter.kt # Gateway 사전 인증 필터
│   ├── GatewaySecretFilter.kt  # Gateway Secret 헤더 검증 필터
│   ├── CustomUserDetailsService.kt
│   └── TeamSecurity.kt         # 팀 권한 검증 빈 (@PreAuthorize 사용)
├── service/
│   ├── AuthService.kt          # 인증 Facade (통합 진입점)
│   ├── AuthenticationService.kt    # 로그인/로그아웃 로직
│   ├── SignupService.kt         # 회원가입 로직
│   ├── TokenService.kt / TokenServiceImpl.kt  # 토큰 발급/갱신/폐기
│   ├── UserService.kt / UserServiceImpl.kt    # 사용자 관리
│   ├── TeamService.kt / TeamServiceImpl.kt    # 팀 관리
│   ├── TeamCreationStrategy.kt  # 팀 생성 전략 인터페이스
│   ├── ManagerTeamCreationStrategy.kt  # 매니저 즉시 생성 전략
│   ├── MemberTeamCreationStrategy.kt   # 일반 멤버 승인 요청 전략
│   ├── InvitationService.kt / InvitationServiceImpl.kt  # 초대 관리
│   ├── JobTitleService.kt / JobTitleServiceImpl.kt      # 직급 관리
│   ├── CompanyService.kt / CompanyServiceImpl.kt        # 회사 관리
│   ├── GuestService.kt          # 게스트 초대/참여
│   ├── PasswordService.kt       # 비밀번호 변경/찾기
│   ├── WithdrawService.kt       # 회원 탈퇴
│   ├── EmailService.kt          # 이메일 발송 (Kafka 연동)
│   └── NotificationEventPublisher.kt  # 알림 이벤트 발행 (Kafka)
└── util/
    └── PrivacyEncryptor.kt      # 개인정보 암호화 유틸리티
```

---

## 환경 설정

주요 환경변수 목록입니다. `application.yml` 및 Docker 환경에서 설정합니다.

| 환경변수 | 설명 | 기본값 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | MySQL 연결 URL | 필수 |
| `SPRING_DATASOURCE_USERNAME` | MySQL 사용자명 | 필수 |
| `SPRING_DATASOURCE_PASSWORD` | MySQL 비밀번호 | 필수 |
| `SPRING_REDIS_HOST` | Redis 호스트 | 필수 |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka 브로커 주소 | `localhost:9092` |
| `AUTH_ENCRYPTION_KEY` | RSA 개인키 암호화 키 (AES-256-GCM) | 필수 |
| `PRIVACY_ENCRYPTION_KEY` | 개인정보 암호화 키 | 필수 |
| `GATEWAY_SHARED_SECRET` | Gateway/Internal 서비스 간 공유 비밀키 | 필수 |
| `JWT_KEY_ID` | JWK Set의 kid 값 | `onmeet-auth-key` |
| `COOKIE_SECURE` | JWT 쿠키 Secure 속성 | `true` |
| `COOKIE_SAMESITE` | JWT 쿠키 SameSite 속성 | `Lax` |
| `APP_API_BASE_URL` | API 기본 URL (게스트 링크 생성 등) | `http://localhost:8080` |
| `INVITATION_EXPIRY_DAYS` | 멤버 초대 만료 기간 (일) | `7` |
| `GUEST_INVITATION_EXPIRY_DAYS` | 게스트 초대 만료 기간 (일) | `1` |
| `INITIAL_TEAM_COLOR` | 기본 팀 색상 | `#FFFFFF` |
| `INITIAL_TEAM_DESCRIPTION` | 기본 팀 설명 | `Initial team` |

### JWT 토큰 유효시간

| 토큰 | 유효시간 |
|---|---|
| Access Token | 1시간 (3600초) |
| Guest Access Token | 4시간 |
| Refresh Token | 7일 (604800초) |
| Guest Refresh Token | 1일 (86400초) |

---

## 보안 아키텍처

### 필터 체인 (순서대로 적용)

1. **`GatewaySecretFilter`** — `/internal/**` 경로의 `X-Gateway-Secret` 헤더 검증
2. **`AuthGatewayPreAuthFilter`** — Gateway가 전달한 사용자 정보 헤더 처리
3. **`JwtAuthenticationFilter`** — JWT 검증 및 Redis 블랙리스트 확인

### 경로별 접근 제어

| 경로 패턴 | 접근 권한 |
|---|---|
| `/v1/register/**`, `/v1/login/**`, `/v1/invitations/**` | Public |
| `/v1/check`, `/v1/refresh`, `/v1/logout` | Public |
| `/v1/.well-known/jwks.json` | Public |
| `/actuator/**` | Public |
| `/v1/manager/**` | `ROLE_MANAGER` 또는 `ROLE_ADMIN` |
| 그 외 `/v1/**` | Authenticated |

### RSA 키 관리

- RSA 2048-bit 키 쌍을 데이터베이스(`server_keys` 테이블)에 저장
- 개인키는 **AES-256-GCM** + PBKDF2(600,000 iterations)로 암호화하여 저장
- 서비스 시작 시 최신 키를 로드하여 사용

---

## 빌드 및 실행

> **주의**: 개별 서비스를 `./gradlew bootRun`으로 직접 실행하면 `.env` 파일이 주입되지 않아 필수 환경변수가 누락됩니다.
> 반드시 `docker compose`를 사용하여 실행하십시오.

### 전체 스택 실행 (권장)
```bash
# 프로젝트 루트에서 실행
docker compose up -d
```

### auth-service만 재시작
```bash
# 이미지 재빌드 후 컨테이너 교체
./gradlew :auth-service:jibDockerBuild
docker compose up -d --no-deps auth-service
```

### 인프라만 실행 (개발 시 인프라 선행 실행)
```bash
# auth-service가 의존하는 인프라만 선택 실행
docker compose up -d mysql-auth redis-auth kafka zookeeper
```

### 테스트
```bash
./gradlew :auth-service:test
```

실행 후 접속:
- **Swagger UI**: `http://localhost:8081/auth/v1/swagger-ui.html`
- **헬스 체크**: `http://localhost:8081/auth/v1/check`
- **JWK Set**: `http://localhost:8081/auth/v1/.well-known/jwks.json`

---

## DB 마이그레이션 (Flyway)

마이그레이션 파일 위치: `src/main/resources/db/migration/`

| 버전 | 설명 |
|---|---|
| V1 | 초기 스키마 — companies, job_titles, users, user_roles, teams, invitations, server_keys |
| V2 | users 테이블에 profile_image_id 컬럼 추가 |
| V3 | team_members 테이블 생성 (팀-사용자 연결, 역할 포함) |
| V4 | teams 테이블에 rejection_reason 컬럼 추가 |
| V5 | withdrawn_users 테이블 생성 (탈퇴 사용자 기록) |
| V6 | guest_invitations 테이블 생성 (UUID 기반 게스트 초대) |
| V7 | users 테이블에 is_password_reset, fcm_device_token 컬럼 추가 |
| V8 | invitations 테이블 code 컬럼에 UNIQUE INDEX 추가 |

`ddl-auto`는 `validate`로 설정되어 있어 Flyway 마이그레이션이 반드시 선행되어야 합니다.

---

## 서비스 간 통신

### Kafka 이벤트 발행 (Producer)

| 토픽 | 목적 | 대상 서비스 |
|---|---|---|
| `notification.send` | 팀 승인/반려 등 알림 이벤트 발행 | notification-service |
| `email.send` | 초대 이메일, 임시 비밀번호 이메일 발송 요청 | email-service |

### REST 호출 (Feign Client)

| 서비스 | 목적 |
|---|---|
| `file-service` | 프로필 이미지 업로드/삭제 (Circuit Breaker 적용) |

### 내부 REST API 제공 (Consumer)

다른 서비스가 `auth-service`의 내부 API를 호출합니다:
- `/internal/users/**` — 사용자 정보 조회 (`X-Gateway-Secret` 인증)
- `/internal/teams/**` — 팀 정보/멤버십 조회 (`X-Gateway-Secret` 인증)
- `/internal/v1/security/**` — 권한 검증 (`X-Internal-Secret` 인증)
