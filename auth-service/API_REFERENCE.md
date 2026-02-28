# Auth Service API Reference

## 개요 (Overview)
`auth-service`는 사용자 인증 및 인가, 토큰 발급, 회원 가입, 초대 검증 등의 기능을 제공합니다.
모든 인증은 **JWT (JSON Web Token)** 기반으로 동작하며, 클라이언트와의 통신에는 **HttpOnly Cookie**를 우선적으로 사용합니다.

## 기본 정보 (Base Info)
- **Base URL**: `/auth` (Gateway 경유 시)
- **Port**: `8081` (Direct), `8080` (Gateway)

---

## 인증 (Authentication)

### 1. 기업 회원가입 (Company Signup)
새로운 회사와 관리자 계정을 생성합니다.

- **URL**: `/register/company`
- **Method**: `POST`
- **Auth**: None

#### Request Body (`CompanySignupRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 관리자 이메일 (로그인 ID) |
| `password` | String | Yes | 비밀번호 |
| `name` | String | Yes | 관리자 이름 |
| `companyName` | String | Yes | 회사명 |
| `teamName` | String | No | 초기 팀 이름 (Default: "General") |

```json
{
  "email": "admin@example.com",
  "password": "strongPassword123!",
  "name": "Admin User",
  "companyName": "Tech Corp",
  "teamName": "Dev Team"
}
```

#### Response
- **Status**: `200 OK`
- **Body**: `Long` (생성된 User ID)

---

### 2. 직원 가입 (Employee Join)
초대 코드를 검증하고 회사에 소속된 직원 계정을 생성합니다.

- **URL**: `/register/join`
- **Method**: `POST`
- **Auth**: None

#### Request Body (`JoinRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 직원 이메일 |
| `password` | String | Yes | 비밀번호 |
| `name` | String | Yes | 직원 이름 |
| `code` | String | Yes | 초대 코드 |
| `employeeId` | String | No | 사번 |

```json
{
  "email": "employee@example.com",
  "password": "password123!",
  "name": "John Doe",
  "code": "INVITE-CODE-123",
  "employeeId": "EMP001"
}
```

#### Response
- **Status**: `200 OK`
- **Body**: `Long` (생성된 User ID)

---

### 3. 로그인 (Login)
이메일과 비밀번호로 인증하고 Access/Refresh 토큰을 발급받습니다.

- **URL**: `/login`
- **Method**: `POST`
- **Auth**: None

#### Request Body (`LoginRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 이메일 |
| `password` | String | Yes | 비밀번호 |

```json
{
  "email": "user@example.com",
  "password": "password123!"
}
```

#### Response
- **Status**: `200 OK`
- **Cookies**:
  - `accessToken`: JWT Access Token (HttpOnly, Secure)
  - `refreshToken`: JWT Refresh Token (HttpOnly, Secure)
- **Body** (`LoginResponse`):
```json
{
  "message": "Login successful",
  "tokenType": "Bearer"
}
```

---

### 4. 로그아웃 (Logout)
현재 사용자의 Access Token을 블랙리스트에 추가하고 쿠키를 삭제합니다.

- **URL**: `/logout`
- **Method**: `POST`
- **Auth**: Required (Cookie or Header)

#### Response
- **Status**: `200 OK`
- **Cookies**: `accessToken`, `refreshToken` 쿠키 삭제 (Max-Age=0)

---

### 5. 토큰 갱신 (Token Refresh)
Refresh Token을 사용하여 새로운 Access Token을 발급받습니다.

- **URL**: `/refresh`
- **Method**: `POST`
- **Auth**: None (Refresh Token Cookie 필요)

#### Request (Cookie)
- `refreshToken`: Valid refresh token cookie

#### Response
- **Status**: `200 OK`
- **Cookies**:
  - `accessToken`: New Access Token
  - `refreshToken`: New Refresh Token (Rotation)

---

### 6. 게스트 로그인 (Guest Login)
회원가입 없이 회의 참여 등을 위해 임시 토큰을 발급받습니다.

- **URL**: `/login/guest`
- **Method**: `POST`
- **Auth**: None

#### Request Body (`GuestLoginRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `name` | String | Yes | 게스트 이름 |
| `meetingId` | String | No | 참여할 회의 ID |

```json
{
  "name": "Guest User",
  "meetingId": "meeting-abc-123"
}
```

#### Response
- **Status**: `200 OK`
- **Body** (`TokenResponse`):
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": null,
  "tokenType": "Bearer"
}
```

---

## 사용자 정보 (User Info)

### 7. 내 정보 조회 (Me)
현재 인증된 사용자의 정보를 간단히 확인합니다.

- **URL**: `/me`
- **Method**: `GET`
- **Auth**: Required

#### Response
- **Status**: `200 OK`
- **Body**: `String` ("Hello, {name}! You are authenticated.")

---

### 8. 인증 상태 확인 (Check)
토큰 유효성을 검사합니다. (Gateway 인증 필터 테스트용)

- **URL**: `/check`
- **Method**: `GET`
- **Auth**: Required

#### Response
- **Status**: `200 OK` (Valid Token)
- **Status**: `401 Unauthorized` (Invalid/Expired Token)

---

## 기타 (Users)

### 9. 초대 검증 (Validate Invitation)
초대 코드의 유효성을 확인하고 초대된 정보를 반환합니다.

- **URL**: `/invitations/validate`
- **Method**: `GET`
- **Auth**: None

#### Parameters
| Param | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 초대된 이메일 |
| `code` | String | Yes | 초대 코드 |

#### Response (`InvitationResponse`)
```json
{
  "email": "invitee@example.com",
  "companyName": "Tech Corp",
  "role": "USER"
}
```

---

## 에러 코드 (Error Codes)

| Status Code | Description |
|---|---|
| `400 Bad Request` | 잘못된 요청 데이터 (유효성 검증 실패) |
| `401 Unauthorized` | 인증 실패, 토큰 만료, 블랙리스트 토큰 |
| `403 Forbidden` | 권한 부족 |
| `404 Not Found` | 리소스 없음 (User not found 등) - `EntityNotFoundException` |
| `409 Conflict` | 중복 데이터 (Email already exists 등) |

---

## 내부 권한 검증 API (Internal Security API)

타 마이크로서비스에서 `auth-service`에 권한 검증을 요청할 때 사용하는 API입니다. **반드시 내부 망에서만 호출되어야 하며, `X-Internal-Secret` 헤더 검증이 필요합니다.**

### 10. 팀장 권한 확인 (Leader Check)

- **URL**: `/internal/v1/security/teams/{teamId}/leader-check`
- **Method**: `GET`
- **Auth**: Internal Secret Header (`X-Internal-Secret`)

#### Parameters
| Param | Type | Required | Description |
|---|---|---|---|
| `teamId` | Long | Yes | (Path) 대상 팀 ID |
| `userId` | Long | Yes | (Query) 조회할 사용자 ID |

#### Response (`SecurityCheckResponse`)
```json
{
  "authorized": true
}
```

---

### 11. 팀 멤버 권한 확인 (Member Check)

- **URL**: `/internal/v1/security/teams/{teamId}/member-check`
- **Method**: `GET`
- **Auth**: Internal Secret Header (`X-Internal-Secret`)

#### Parameters
| Param | Type | Required | Description |
|---|---|---|---|
| `teamId` | Long | Yes | (Path) 대상 팀 ID |
| `userId` | Long | Yes | (Query) 조회할 사용자 ID |

#### Response (`SecurityCheckResponse`)
```json
{
  "authorized": true
}
```

---

### 12. 동일 회사 여부 확인 (Company Check)

- **URL**: `/internal/v1/security/teams/{teamId}/company-check`
- **Method**: `GET`
- **Auth**: Internal Secret Header (`X-Internal-Secret`)

#### Parameters
| Param | Type | Required | Description |
|---|---|---|---|
| `teamId` | Long | Yes | (Path) 대상 팀 ID |
| `userId` | Long | Yes | (Query) 조회할 사용자 ID |

#### Response (`SecurityCheckResponse`)
```json
{
  "authorized": true
}
```
