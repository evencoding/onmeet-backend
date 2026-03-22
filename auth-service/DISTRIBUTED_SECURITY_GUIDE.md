# 분산 보안 및 권한 검증 가이드 (Distributed Security Guide)

이 문서는 OnMeet 마이크로서비스 아키텍처에서 서비스 간 권한 검증을 통합하고 일관된 보안 정책을 적용하는 방법을 설명합니다.

---

## 1. 아키텍처 개요 (Architecture Overview)

각 마이크로서비스는 직접 DB에 접근하여 권한을 확인하는 대신, `auth-service`에서 제공하는 **내부 보안 API**를 호출하여 권한을 위임합니다.

- **인가 흐름**:
  1. 클라이언트 요청 (Gateway 인증 완료)
  2. 서비스 Controller 메소드 진입 전 `@PreAuthorize` 가동
  3. `teamSecurity` 빈이 Feign Client를 통해 `auth-service` 호출
  4. `auth-service`에서 권한 확인 후 응답
  5. 검증 성공 시 비즈니스 로직 수행

### 지원하는 권한 검증 타입

| 메소드 | 내부 API | 설명 |
|---|---|---|
| `teamSecurity.isLeaderOf(teamId, principal)` | `/internal/v1/security/teams/{teamId}/leader-check` | 팀장 권한 확인 |
| `teamSecurity.isMemberOf(teamId, principal)` | `/internal/v1/security/teams/{teamId}/member-check` | 팀 멤버십 확인 |
| `teamSecurity.belongsToSameCompany(teamId, principal)` | `/internal/v1/security/teams/{teamId}/company-check` | 동일 회사 여부 확인 |

---

## 2. 서비스 통합 단계 (Integration Steps)

타 서비스(Video, Chat 등)에서 보안 기능을 활성화하려면 다음 단계를 따르십시오.

### 2.1. 의존성 추가 (`build.gradle`)
Feign Client 및 공통 모듈이 필요합니다.

```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.cloud:spring-cloud-starter-loadbalancer'
    implementation project(':onmeet-common')
}
```

### 2.2. 애플리케이션 설정 (`*Application.java`)
Feign Client를 활성화하고 공통 보안 패키지를 스캔 대상에 포함해야 합니다.

```java
@SpringBootApplication(scanBasePackages = {"com.onmeet.your-service", "com.onmeet.common.security"})
@EnableFeignClients(basePackages = "com.onmeet.common.client")
public class YourApplication { ... }
```

### 2.3. 보안 설정 (`SecurityConfig.java`)
메소드 단위 보안(`@PreAuthorize`)을 활성화합니다.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig { ... }
```

### 2.4. 환경 설정 (`application.yml`)
`auth-service`의 내부 URL 및 Gateway와 공유하는 비밀키가 필요합니다.

```yaml
gateway:
  shared-secret: ${GATEWAY_SHARED_SECRET}

onmeet:
  auth:
    internal-url: ${AUTH_INTERNAL_URL:http://auth-service:8081}
```

---

## 3. 권한 체크 사용법 (Usage)

`onmeet-common` 모듈에 정의된 `teamSecurity` 빈을 사용하여 간편하게 권한을 확인할 수 있습니다.

### 3.1. 팀 멤버십 확인
```java
@GetMapping("/teams/{teamId}/resource")
@PreAuthorize("@teamSecurity.isMemberOf(#teamId, principal)")
public String getResource(@PathVariable Long teamId) {
    return "Team Access Granted";
}
```

### 3.2. 팀장 권한 확인
```java
@PostMapping("/teams/{teamId}/config")
@PreAuthorize("@teamSecurity.isLeaderOf(#teamId, principal)")
public void updateConfig(@PathVariable Long teamId) {
    // 로직 수행
}
```

### 3.3. 동일 회사 여부 확인
```java
@PostMapping("/teams/{teamId}/approve")
@PreAuthorize("hasRole('MANAGER') and @teamSecurity.belongsToSameCompany(#teamId, principal)")
public void approveTeam(@PathVariable Long teamId) {
    // 로직 수행
}
```

---

## 4. 보안 프로토콜 (Internal Protocol)

서비스 간 통신 시 보안을 위해 다음 규칙이 적용됩니다.

- **인증 헤더**: 모든 내부 보안 요청에는 `X-Internal-Secret` 헤더가 포함되어야 합니다.
- **검증**: `auth-service`는 헤더의 값이 `gateway.shared-secret`과 일치하는지 확인합니다.
- **데이터 타입**: 사용자 ID(`principal`)는 객체 타입에 따라 자동으로 `Long`으로 변환되어 처리됩니다.
- **응답 형식**: `SecurityCheckResponse { authorized: Boolean }`

---

## 5. auth-service 내부 API 목록

| HTTP Method | Path | 설명 |
|---|---|---|
| GET | `/internal/v1/security/teams/{teamId}/leader-check?userId={userId}` | 팀장 권한 확인 |
| GET | `/internal/v1/security/teams/{teamId}/member-check?userId={userId}` | 팀 멤버십 확인 |
| GET | `/internal/v1/security/teams/{teamId}/company-check?userId={userId}` | 동일 회사 여부 확인 |
| GET | `/internal/users/{userId}` | 사용자 정보 조회 (Gateway Secret) |
| POST | `/internal/users/batch` | 다중 사용자 정보 조회 (Gateway Secret) |
| GET | `/internal/users/{userId}/exists` | 사용자 존재 여부 확인 (Gateway Secret) |
| POST | `/internal/users/exists/batch` | 다중 사용자 존재 여부 확인 (Gateway Secret) |
| GET | `/internal/teams/{teamId}/exists` | 팀 존재 여부 확인 (Gateway Secret) |
| POST | `/internal/teams/membership/check` | 팀 멤버십 확인 (Gateway Secret) |

---

## 6. 관련 파일 링크
- [InternalSecurityClient.kt (onmeet-common)](file:///Users/sprtms16/IdeaProjects/onmeet-backend/onmeet-common/src/main/kotlin/com/onmeet/common/client/InternalSecurityClient.kt)
- [RemoteTeamSecurity.kt (onmeet-common)](file:///Users/sprtms16/IdeaProjects/onmeet-backend/onmeet-common/src/main/kotlin/com/onmeet/common/security/RemoteTeamSecurity.kt)
- [InternalSecurityController.kt (auth-service)](file:///Users/sprtms16/IdeaProjects/onmeet-backend/auth-service/src/main/kotlin/com/onmeet/auth/controller/internal/InternalSecurityController.kt)
