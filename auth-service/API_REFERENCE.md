# Auth Service API Reference

**Version**: v0.2.0
**Last Updated**: 2026-03-14
**Status**: Production

## 개요 (Overview)
`auth-service`는 사용자 인증 및 인가, 토큰 발급, 회원 가입, 초대 검증, 팀/직급 관리 등의 기능을 제공합니다.
모든 인증은 **JWT (JSON Web Token, RS256)** 기반으로 동작하며, 클라이언트와의 통신에는 **HttpOnly Cookie**를 우선적으로 사용합니다.

## 기본 정보 (Base Info)
- **Base URL**: `/auth` (Gateway 경유 시)
- **Port**: `8081` (Direct), `8080` (Gateway)
- **Context Path**: `/auth`

## 에러 응답 형식

모든 에러는 `BusinessException` + `ErrorCode` 기반의 `ErrorResponse`로 반환됩니다.

```json
{
  "code": "AUTH_001",
  "status": 409,
  "message": "이미 사용 중인 이메일입니다",
  "timestamp": 1710000000000
}
```

---

## 인증 (Authentication)

### 1. 기업(관리자) 회원가입 (Company Signup)
새로운 기업을 등록하고 관리자 계정을 생성합니다.

- **URL**: `/v1/register/company`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **Auth**: None

#### Request (Multipart)
| Part | Type | Required | Description |
|---|---|---|---|
| `request` | JSON | Yes | 회원가입 정보 (CompanySignupRequest) |
| `profileImage` | File | No | 프로필 이미지 파일 |

**CompanySignupRequest**:
| Field | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 관리자 이메일 (로그인 ID) |
| `password` | String | Yes | 비밀번호 |
| `name` | String | Yes | 관리자 이름 |
| `companyName` | String | Yes | 회사명 |

```json
{
  "email": "admin@company.com",
  "password": "Password123!",
  "name": "Admin User",
  "companyName": "Tech Corp"
}
```

#### Response
- **Status**: `200 OK`
- **Body**: `Long` (생성된 User ID)

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_001` | 409 | 이미 사용 중인 이메일 |
| `AUTH_008` | 409 | 이미 존재하는 회사명 |
| `COMMON_VALIDATION_FAILED` | 400 | 입력값 검증 실패 |

---

### 2. 사원(멤버) 회원가입 (Employee Join)
초대 코드를 검증하고 기업에 소속된 직원 계정을 생성합니다.

- **URL**: `/v1/register/join`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **Auth**: None

#### Request (Multipart)
| Part | Type | Required | Description |
|---|---|---|---|
| `request` | JSON | Yes | 가입 정보 (JoinRequest) |
| `profileImage` | File | No | 프로필 이미지 파일 |

**JoinRequest**:
| Field | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 직원 이메일 |
| `code` | String | Yes | 초대 코드 |
| `password` | String | Yes | 비밀번호 |
| `name` | String | Yes | 직원 이름 |
| `employeeId` | String | No | 사번 |

```json
{
  "email": "employee@company.com",
  "code": "INV-123456",
  "password": "Password123!",
  "name": "Jane Doe",
  "employeeId": "EMP-001"
}
```

#### Response
- **Status**: `200 OK`
- **Body**: `Long` (생성된 User ID)

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_001` | 409 | 이미 사용 중인 이메일 |
| `AUTH_002` | 404 | 초대 코드를 찾을 수 없음 |
| `AUTH_003` | 400 | 유효하지 않은 초대 코드 또는 이메일 불일치 |

---

### 3. 초대 코드 검증 (Validate Invitation)
이메일과 초대 코드를 검증하여 유효한 초대인지 확인합니다.

- **URL**: `/v1/invitations/validate`
- **Method**: `GET`
- **Auth**: None

#### Query Parameters
| Param | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 초대된 이메일 |
| `code` | String | Yes | 초대 코드 |

#### Response (`InvitationResponse`)
```json
{
  "id": 1,
  "email": "invitee@company.com",
  "code": "INV-123456",
  "company": { "id": 1, "name": "Tech Corp" },
  "role": "USER",
  "expiresAt": "2026-03-21T00:00:00"
}
```

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_002` | 404 | 초대 코드를 찾을 수 없음 |
| `AUTH_003` | 400 | 유효하지 않은 초대 코드 또는 이메일 불일치 |

---

### 4. 로그인 (Login)
이메일과 비밀번호로 인증하고 Access/Refresh 토큰을 쿠키로 발급받습니다.

- **URL**: `/v1/login`
- **Method**: `POST`
- **Auth**: None

#### Request Body (`LoginRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 이메일 |
| `password` | String | Yes | 비밀번호 |
| `deviceToken` | String | No | FCM 디바이스 토큰 |

```json
{
  "email": "user@company.com",
  "password": "Password123!",
  "deviceToken": "fcm-token-optional"
}
```

#### Response
- **Status**: `200 OK`
- **Cookies**:
  - `accessToken`: JWT Access Token (HttpOnly, Secure, SameSite=Lax, MaxAge=3600)
  - `refreshToken`: JWT Refresh Token (HttpOnly, Secure, SameSite=Lax, MaxAge=604800)
- **Body** (`LoginResponse`):
```json
{
  "message": "Login successful",
  "tokenType": "Bearer"
}
```

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_004` | 401 | 이메일 또는 비밀번호 불일치 |
| `COMMON_VALIDATION_FAILED` | 400 | 필수 필드 누락 |

---

### 5. 게스트 로그인 (Guest Login)
회원가입 없이 게스트 토큰을 쿠키로 발급받습니다. Access Token 유효시간은 4시간, Refresh Token은 1일입니다.

- **URL**: `/v1/login/guest`
- **Method**: `POST`
- **Auth**: None

#### Request Body (`GuestLoginRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `name` | String | Yes | 게스트 이름 |
| `meetingId` | String | No | 참여할 미팅 ID |

```json
{
  "name": "Guest User",
  "meetingId": "meeting-abc-123"
}
```

#### Response
- **Status**: `200 OK`
- **Cookies**:
  - `accessToken`: Guest JWT Access Token (4시간 유효)
  - `refreshToken`: Guest Refresh Token (1일 유효)
- **Body**: 없음 (No Content)

---

### 6. 로그아웃 (Logout)
Access Token을 블랙리스트에 추가하고 쿠키를 삭제합니다.

- **URL**: `/v1/logout`
- **Method**: `POST`
- **Auth**: Optional (Cookie 또는 Authorization 헤더)

#### Response
- **Status**: `200 OK`
- **Cookies**: `accessToken`, `refreshToken` 쿠키 삭제 (Max-Age=0)

---

### 7. 토큰 갱신 (Token Refresh)
Refresh Token을 사용하여 새로운 Access/Refresh 토큰을 발급받습니다 (Rotation).

- **URL**: `/v1/refresh`
- **Method**: `POST`
- **Auth**: None (Refresh Token Cookie 또는 Request Body 필요)

#### Request
- **Cookie**: `refreshToken` 쿠키
- **또는 Body** (`RefreshRequest`):
```json
{
  "refreshToken": "uuid-refresh-token"
}
```

#### Response
- **Status**: `200 OK`
- **Cookies**:
  - `accessToken`: 새로운 Access Token
  - `refreshToken`: 새로운 Refresh Token (Rotation)
- **Body** (`TokenResponse`):
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "new-refresh-uuid",
  "tokenType": "Bearer"
}
```

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_005` | 400 | 유효하지 않은 Refresh Token |

---

### 8. 헬스 체크 (Check)
서비스 생존 여부를 확인합니다.

- **URL**: `/v1/check`
- **Method**: `GET`
- **Auth**: None

#### Response
- **Status**: `200 OK`

---

### 9. JWK Set 조회 (JWKS)
OAuth2 Resource Server에서 토큰 서명을 검증하기 위한 공개키 목록을 반환합니다.

- **URL**: `/v1/.well-known/jwks.json`
- **Method**: `GET`
- **Auth**: None

#### Response
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "onmeet-auth-key",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

---

### 10. 비밀번호 찾기 (Find Password)
이메일로 임시 비밀번호를 발송합니다. Kafka를 통해 email-service로 비동기 발송됩니다.

- **URL**: `/v1/password/find`
- **Method**: `POST`
- **Auth**: None

#### Request Body (`FindPasswordRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | 가입된 이메일 주소 |

```json
{
  "email": "user@company.com"
}
```

#### Response
- **Status**: `200 OK`
- **Body**:
```json
{
  "message": "Temporary password has been sent to your email"
}
```

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_011` | 404 | 해당 이메일로 등록된 사용자 없음 |
| `COMMON_VALIDATION_FAILED` | 400 | 이메일 형식 오류 |

---

## 회원 관리 (Member Management)

### 11. 내 정보 조회 (Get My Info)
현재 로그인한 사용자의 상세 정보를 조회합니다.

- **URL**: `/v1/member/me`
- **Method**: `GET`
- **Auth**: Required

#### Response (`UserResponseDto`)
```json
{
  "id": 1,
  "email": "user@company.com",
  "name": "John Doe",
  "employeeId": "EMP-001",
  "roles": ["USER"],
  "status": "ACTIVE",
  "company": {
    "id": 1,
    "name": "Tech Corp"
  },
  "jobTitle": {
    "id": 2,
    "name": "대리",
    "isDefault": false
  },
  "teams": [
    { "id": 10, "name": "Dev Team", "color": "#FF5733" }
  ],
  "profileImageId": 5,
  "isPasswordReset": false
}
```

---

### 12. 내 프로필 수정 (Update My Profile)
현재 로그인한 사용자의 프로필 정보와 이미지를 수정합니다.

- **URL**: `/v1/member/me`
- **Method**: `PATCH`
- **Auth**: Required
- **Content-Type**: `multipart/form-data`

#### Request (Multipart)
| Part | Type | Required | Description |
|---|---|---|---|
| `request` | JSON | Yes | 프로필 정보 (UserProfileUpdateRequest) |
| `profileImage` | File | No | 프로필 이미지 파일 |

**UserProfileUpdateRequest**:
| Field | Type | Required | Description |
|---|---|---|---|
| `name` | String | No | 이름 (2~50자) |
| `employeeId` | String | No | 사번 (최대 50자) |
| `jobTitleId` | Long | No | 직급 ID |

```json
{
  "name": "John Updated",
  "employeeId": "EMP-002",
  "jobTitleId": 5
}
```

#### Response
- **Status**: `200 OK`
- **Body**: `UserResponseDto`

---

### 13. 비밀번호 변경 (Change Password)
현재 비밀번호를 확인 후 새 비밀번호로 변경합니다.

- **URL**: `/v1/member/me/password`
- **Method**: `PUT`
- **Auth**: Required

#### Request Body (`ChangePasswordRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `currentPassword` | String | Yes | 현재 비밀번호 |
| `newPassword` | String | Yes | 새 비밀번호 |

```json
{
  "currentPassword": "oldPassword123!",
  "newPassword": "newPassword456!"
}
```

#### Response
- **Status**: `200 OK`

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_009` | 401 | 현재 비밀번호 불일치 |

---

### 14. 프로필 이미지 삭제 (Delete Profile Image)
현재 로그인한 사용자의 프로필 이미지를 삭제합니다.

- **URL**: `/v1/member/me/profile-image`
- **Method**: `DELETE`
- **Auth**: Required

#### Response
- **Status**: `200 OK`
- **Body**: `UserResponseDto` (업데이트된 사용자 정보)

---

### 15. 회원 탈퇴 (Withdraw)
비밀번호 검증 후 회원을 탈퇴 처리합니다.

- **URL**: `/v1/member/me`
- **Method**: `DELETE`
- **Auth**: Required

#### Request Body (`WithdrawRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `password` | String | Yes | 본인 확인 비밀번호 |

```json
{
  "password": "myPassword123!"
}
```

#### Response
- **Status**: `204 No Content`

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_009` | 401 | 비밀번호 불일치 |

---

### 16. 회원 정보 조회 (Get Member Info)
특정 회원의 공개 프로필을 조회합니다.

- **URL**: `/v1/member/{memberId}`
- **Method**: `GET`
- **Auth**: Required

#### Path Parameters
| Param | Type | Description |
|---|---|---|
| `memberId` | Long | 조회할 회원 ID |

#### Response
- **Status**: `200 OK`
- **Body**: `UserResponseDto`

---

## 팀 관리 (Team Management)

### 17. 팀 생성 요청 (Create Team)
팀 생성을 요청합니다. 일반 직원은 `PENDING_APPROVAL` 상태로 생성되며 매니저 승인이 필요합니다. 매니저는 즉시 `ACTIVE` 상태로 생성됩니다.

- **URL**: `/v1/member/teams`
- **Method**: `POST`
- **Auth**: Required

#### Request Body (`TeamRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `name` | String | Yes | 팀 이름 |
| `description` | String | No | 팀 설명 |
| `color` | String | No | 팀 색상 (Hex Code, e.g. `#FF5733`) |
| `memberIds` | List\<Long\> | No | 팀원 ID 목록 (MANAGER 전용) |
| `leaderId` | Long | No | 팀장 ID (MANAGER 전용, memberIds 중 한 명이어야 함) |

```json
{
  "name": "Frontend Team",
  "description": "Frontend development team",
  "color": "#3498DB"
}
```

#### Response
- **Status**: `200 OK`
- **Body**: `Long` (생성된 팀 ID)

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_019` | 409 | 이미 같은 이름의 팀이 존재함 |

---

### 18. 팀장 위임 (Delegate Leader)
현 팀장이 팀장직을 다른 팀원에게 위임합니다. 매니저도 가능합니다.

- **URL**: `/v1/member/teams/{teamId}/delegate/{userId}`
- **Method**: `POST`
- **Auth**: Required (팀장 또는 MANAGER)

#### Path Parameters
| Param | Type | Description |
|---|---|---|
| `teamId` | Long | 대상 팀 ID |
| `userId` | Long | 새 팀장으로 지정할 사용자 ID |

#### Response
- **Status**: `200 OK`

---

### 19. 팀 해체 (Dissolve Team)
팀을 비활성화합니다. 팀장 또는 매니저만 가능합니다.

- **URL**: `/v1/member/teams/{teamId}`
- **Method**: `DELETE`
- **Auth**: Required (팀장 또는 MANAGER)

#### Path Parameters
| Param | Type | Description |
|---|---|---|
| `teamId` | Long | 해체할 팀 ID |

#### Response
- **Status**: `200 OK`

---

### 20. 팀 생성 요청 취소 (Cancel Team Request)
자신이 요청한 팀 생성을 취소합니다. `PENDING_APPROVAL` 상태에서만 가능합니다.

- **URL**: `/v1/member/teams/{teamId}/cancel`
- **Method**: `DELETE`
- **Auth**: Required

#### Path Parameters
| Param | Type | Description |
|---|---|---|
| `teamId` | Long | 취소할 팀 ID |

#### Response
- **Status**: `200 OK`

---

### 21. 직급 목록 조회 (Get Job Titles)
현재 소속된 회사의 모든 직급 목록을 조회합니다.

- **URL**: `/v1/member/job-titles`
- **Method**: `GET`
- **Auth**: Required

#### Response
- **Status**: `200 OK`
- **Body**: `List<JobTitleResponse>`
```json
[
  { "id": 1, "name": "사원", "isDefault": true },
  { "id": 2, "name": "대리", "isDefault": false }
]
```

---

## 관리자 전용 API (Manager Management)

모든 엔드포인트는 `ROLE_MANAGER` 또는 `ROLE_ADMIN` 권한이 필요합니다.

### 22. 전체 사원 목록 조회 (Get All Employees)
현재 기업의 모든 사원 목록을 페이징하여 조회합니다.

- **URL**: `/v1/manager/employees`
- **Method**: `GET`
- **Auth**: Required (MANAGER/ADMIN)

#### Query Parameters (Pageable)
| Param | Type | Default | Description |
|---|---|---|---|
| `page` | Integer | 0 | 페이지 번호 (0-indexed) |
| `size` | Integer | 20 | 페이지 크기 |
| `sort` | String | - | 정렬 기준 |

#### Response (`PageResponse<UserResponseDto>`)
```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 100,
  "totalPages": 5,
  "last": false
}
```

---

### 23. 사원 계정 비활성화 (Deactivate User)
특정 사원의 계정을 비활성화합니다.

- **URL**: `/v1/manager/employees/{userId}/deactivate`
- **Method**: `PUT`
- **Auth**: Required (MANAGER/ADMIN)

#### Response
- **Status**: `200 OK`
- **Body**: `UserResponseDto`

---

### 24. 사원 계정 활성화 (Activate User)
특정 사원의 계정을 활성화합니다.

- **URL**: `/v1/manager/employees/{userId}/activate`
- **Method**: `PUT`
- **Auth**: Required (MANAGER/ADMIN)

#### Response
- **Status**: `200 OK`
- **Body**: `UserResponseDto`

---

### 25. 사원 프로필 이미지 초기화 (Reset Profile Image)
특정 사원의 프로필 이미지를 기본값으로 초기화합니다.

- **URL**: `/v1/manager/employees/{userId}/profile-image`
- **Method**: `DELETE`
- **Auth**: Required (MANAGER/ADMIN)

#### Response
- **Status**: `204 No Content`

---

### 26. 팀 생성 요청 승인 (Approve Team)
팀 생성 요청(`PENDING_APPROVAL`)을 승인합니다. 매니저가 동일 기업 소속이어야 합니다.

- **URL**: `/v1/manager/teams/{teamId}/approve`
- **Method**: `POST`
- **Auth**: Required (MANAGER, 동일 기업)

#### Response
- **Status**: `200 OK`

---

### 27. 팀 생성 요청 반려 (Reject Team)
팀 생성 요청을 반려합니다. 사유를 입력할 수 있습니다.

- **URL**: `/v1/manager/teams/{teamId}/reject`
- **Method**: `POST`
- **Auth**: Required (MANAGER, 동일 기업)

#### Request Body (`TeamRejectRequest`, Optional)
```json
{
  "reason": "팀 목적이 불명확합니다"
}
```

#### Response
- **Status**: `200 OK`

---

### 28. 팀장 임명 (Assign Leader)
특정 팀에 팀장을 임명합니다.

- **URL**: `/v1/manager/teams/{teamId}/leader/{userId}`
- **Method**: `POST`
- **Auth**: Required (MANAGER, 동일 기업)

#### Response
- **Status**: `200 OK`

---

### 29. 멤버 초대 (Invite Member)
이메일 리스트로 새로운 멤버를 기업에 초대합니다. 최대 100개 이메일을 한 번에 초대 가능합니다.

- **URL**: `/v1/manager/invite`
- **Method**: `POST`
- **Auth**: Required (MANAGER)

#### Request Body (`InvitationRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `emails` | List\<String\> | Yes | 초대할 이메일 리스트 (최대 100개) |

```json
{
  "emails": ["user1@company.com", "user2@company.com"]
}
```

#### Response
- **Status**: `200 OK`
- **Body**: `List<Long>` (생성된 초대 ID 목록)

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_037` | 409 | 해당 이메일로 이미 유효한 초대가 존재함 |
| `AUTH_035` | 404 | 회사를 찾을 수 없음 |
| `COMMON_VALIDATION_FAILED` | 400 | 이메일 형식 오류 또는 빈 리스트 |

---

### 30. 직급 생성 (Create Job Title)
새로운 직급을 생성합니다.

- **URL**: `/v1/manager/job-titles`
- **Method**: `POST`
- **Auth**: Required (MANAGER/ADMIN)

#### Request Body (`JobTitleRequest`)
| Field | Type | Required | Description |
|---|---|---|---|
| `name` | String | Yes | 직급명 |
| `isDefault` | Boolean | No | 기본값 여부 (Default: false) |

```json
{
  "name": "과장",
  "isDefault": false
}
```

#### Response
- **Status**: `200 OK`
- **Body**: `JobTitleResponse`
```json
{
  "id": 3,
  "name": "과장",
  "isDefault": false
}
```

---

### 31. 직급 수정 (Update Job Title)
기존 직급 정보를 수정합니다.

- **URL**: `/v1/manager/job-titles/{id}`
- **Method**: `PUT`
- **Auth**: Required (MANAGER/ADMIN)

#### Response
- **Status**: `200 OK`
- **Body**: `JobTitleResponse`

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_014` | 404 | 직급을 찾을 수 없음 |

---

### 32. 직급 삭제 (Delete Job Title)
직급을 삭제합니다.

- **URL**: `/v1/manager/job-titles/{id}`
- **Method**: `DELETE`
- **Auth**: Required (MANAGER/ADMIN)

#### Response
- **Status**: `200 OK`

---

## 게스트 관리 (Guest Management)

### 33. 게스트 초대 (Invite Guest)
특정 미팅에 게스트를 초대하는 이메일을 발송합니다.

- **URL**: `/v1/guests/invite`
- **Method**: `POST`
- **Auth**: Required

#### Request Body (`GuestInviteRequestDto`)
게스트 초대에 필요한 정보 (meetingId, guestEmail, hostName, roomName 등 포함)

#### Response
- **Status**: `200 OK`

---

### 34. 게스트 미팅 참여 (Join Meeting via Link)
초대 링크(UUID)를 통해 게스트가 미팅에 참여합니다. 쿠키를 발급하고 미팅 페이지로 리다이렉트합니다.

- **URL**: `/v1/guests/join/{uuid}`
- **Method**: `GET`
- **Auth**: None

#### Path Parameters
| Param | Type | Description |
|---|---|---|
| `uuid` | String | 게스트 초대 UUID |

#### Response
- **Status**: Redirect (미팅 페이지)
- **Cookies**: `accessToken`, `refreshToken` 발급

#### Errors
| Code | Status | Description |
|---|---|---|
| `AUTH_044` | 400 | 유효하지 않거나 존재하지 않는 게스트 초대 링크 |
| `AUTH_045` | 400 | 게스트 초대 링크 만료 |

---

## 내부 사용자 API (Internal User API)

Gateway Secret(`X-Gateway-Secret` 헤더)으로 인증된 서비스 간 통신 전용입니다.

### 35. 사용자 정보 조회

- **URL**: `/internal/users/{userId}`
- **Method**: `GET`
- **Auth**: `X-Gateway-Secret` 헤더

#### Response (`UserInfoDto`)
```json
{
  "userId": 1,
  "name": "John Doe",
  "email": "user@company.com",
  "profileImageId": 5
}
```

---

### 36. 다중 사용자 정보 조회 (Batch)

- **URL**: `/internal/users/batch`
- **Method**: `POST`
- **Auth**: `X-Gateway-Secret` 헤더

#### Request Body (`BatchUserInfoRequest`)
```json
{
  "userIds": [1, 2, 3]
}
```

#### Response (`BatchUserInfoResponse`)
```json
{
  "users": [
    { "userId": 1, "name": "John Doe", "email": "john@company.com", "profileImageId": 1 }
  ]
}
```

---

### 37. 사용자 존재 여부 확인

- **URL**: `/internal/users/{userId}/exists`
- **Method**: `GET`
- **Auth**: `X-Gateway-Secret` 헤더

#### Response (`UserExistsResponse`)
```json
{
  "exists": true
}
```

---

### 38. 다중 사용자 존재 여부 확인 (Batch)

- **URL**: `/internal/users/exists/batch`
- **Method**: `POST`
- **Auth**: `X-Gateway-Secret` 헤더

#### Request Body (`BatchUserExistsRequest`)
```json
{
  "userIds": [1, 2, 3]
}
```

#### Response (`BatchUserExistsResponse`)
```json
{
  "results": { "1": true, "2": true, "3": false }
}
```

---

## 내부 팀 API (Internal Team API)

### 39. 팀 존재 여부 확인

- **URL**: `/internal/teams/{teamId}/exists`
- **Method**: `GET`
- **Auth**: `X-Gateway-Secret` 헤더

#### Response (`TeamExistsResponse`)
```json
{
  "exists": true
}
```

---

### 40. 팀 멤버십 확인

- **URL**: `/internal/teams/membership/check`
- **Method**: `POST`
- **Auth**: `X-Gateway-Secret` 헤더

#### Request Body (`TeamMembershipRequest`)
```json
{
  "teamId": 1,
  "userId": 10
}
```

#### Response (`TeamMembershipResponse`)
```json
{
  "isMember": true
}
```

---

## 내부 보안 API (Internal Security API)

타 마이크로서비스에서 권한 검증을 요청할 때 사용합니다. `X-Internal-Secret` 헤더 인증이 필요합니다.
`onmeet-common`의 `RemoteTeamSecurity` 빈을 통해 호출됩니다.

### 41. 팀장 권한 확인 (Leader Check)

- **URL**: `/internal/v1/security/teams/{teamId}/leader-check`
- **Method**: `GET`
- **Auth**: `X-Internal-Secret` 헤더

#### Query Parameters
| Param | Type | Description |
|---|---|---|
| `userId` | Long | 조회할 사용자 ID |

#### Response (`SecurityCheckResponse`)
```json
{
  "authorized": true
}
```

---

### 42. 팀 멤버 권한 확인 (Member Check)

- **URL**: `/internal/v1/security/teams/{teamId}/member-check`
- **Method**: `GET`
- **Auth**: `X-Internal-Secret` 헤더

#### Query Parameters
| Param | Type | Description |
|---|---|---|
| `userId` | Long | 조회할 사용자 ID |

#### Response (`SecurityCheckResponse`)
```json
{
  "authorized": true
}
```

---

### 43. 동일 회사 여부 확인 (Company Check)

- **URL**: `/internal/v1/security/teams/{teamId}/company-check`
- **Method**: `GET`
- **Auth**: `X-Internal-Secret` 헤더

#### Query Parameters
| Param | Type | Description |
|---|---|---|
| `userId` | Long | 조회할 사용자 ID |

#### Response (`SecurityCheckResponse`)
```json
{
  "authorized": true
}
```

---

## 에러 코드 전체 목록 (Error Codes)

| Code | HTTP Status | Description |
|---|---|---|
| `AUTH_001` | 409 | 이미 사용 중인 이메일 |
| `AUTH_002` | 404 | 초대 코드를 찾을 수 없음 |
| `AUTH_003` | 400 | 유효하지 않은 초대 코드 또는 이메일 불일치 |
| `AUTH_004` | 401 | 이메일 또는 비밀번호 불일치 (인증 실패) |
| `AUTH_005` | 400 | 유효하지 않은 Refresh Token |
| `AUTH_006` | 404 | 사용자를 찾을 수 없음 |
| `AUTH_008` | 409 | 이미 존재하는 회사 |
| `AUTH_009` | 401 | 비밀번호 불일치 |
| `AUTH_011` | 404 | 해당 이메일로 등록된 사용자 없음 |
| `AUTH_014` | 404 | 직급을 찾을 수 없음 |
| `AUTH_019` | 409 | 이미 같은 이름의 팀이 존재함 |
| `AUTH_025` | 404 | 팀을 찾을 수 없음 |
| `AUTH_035` | 404 | 회사를 찾을 수 없음 |
| `AUTH_037` | 409 | 해당 이메일로 이미 유효한 초대가 존재함 |
| `AUTH_044` | 400 | 유효하지 않거나 존재하지 않는 게스트 초대 링크 |
| `AUTH_045` | 400 | 게스트 초대 링크 만료 |
| `COMMON_VALIDATION_FAILED` | 400 | 입력값 검증 실패 |
| `COMMON_ACCESS_DENIED` | 403 | 권한 없음 |
| `COMMON_INTERNAL_ERROR` | 500 | 서버 내부 오류 |
