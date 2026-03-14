# 로컬 개발 환경 로그인 테스트 가이드

이 프로젝트는 **쿠키(Cookie) 기반 인증**을 기본으로 사용합니다. 따라서 로컬 테스트 시에도 쿠키를 저장하고 전송하는 방식을 권장합니다.

## 쿠키 정보

| 쿠키명 | 설명 | 유효시간 | 속성 |
|---|---|---|---|
| `accessToken` | JWT Access Token | 1시간 (3600초) | HttpOnly, Secure, SameSite=Lax |
| `refreshToken` | Refresh Token | 7일 (604800초) | HttpOnly, Secure, SameSite=Lax |

> **SameSite 설정**: `COOKIE_SAMESITE` 환경변수로 제어 가능 (기본값: `Lax`)
> **Secure 설정**: `COOKIE_SECURE` 환경변수로 제어 가능 (기본값: `true`)
> 로컬 개발 환경에서 HTTP로 테스트할 경우 `COOKIE_SECURE=false`로 설정해야 합니다.

---

## 1. 사전 준비 (Prerequisites)

`auth-service`가 정상적으로 실행되어 있어야 합니다.

- **Auth Service URL**: `http://localhost:8080/auth` (Gateway 경유)
- **Direct URL**: `http://localhost:8081/auth` (Direct)

---

## 2. 쿠키 기반 테스트 (권장)

`curl`의 쿠키 저장(`-c`) 및 전송(`-b`) 옵션을 사용하면 브라우저와 동일한 환경을 시뮬레이션할 수 있습니다.

### 2.1. 로그인 및 쿠키 저장

로그인 성공 시 서버가 반환하는 `accessToken` 및 `refreshToken` 쿠키를 파일(`cookies.txt`)에 저장합니다.

```bash
# -c cookies.txt: 서버에서 받은 쿠키를 파일에 저장
curl -c cookies.txt -X POST http://localhost:8080/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "dev@test.com",
    "password": "Password123!"
  }'
```

**응답 확인:**
정상 로그인 시 아래와 같은 응답과 함께 `cookies.txt` 파일이 생성됩니다.
```json
{
  "message": "Login successful",
  "tokenType": "Bearer"
}
```

### 2.2. 다른 서비스 호출 (쿠키 사용)

저장된 쿠키 파일을 사용하여 인증이 필요한 다른 서비스를 호출합니다.

```bash
# -b cookies.txt: 저장된 쿠키를 요청에 포함
curl -b cookies.txt -X GET http://localhost:8080/meetings/123
```

---

## 3. 헤더 기반 테스트 (개발자 편의용)

`auth-service` 및 각 서비스의 필터(`JwtAuthenticationFilter`)는 개발 편의를 위해 **Authorization 헤더** 방식도 지원합니다.

### 3.1. 토큰 추출

로그인 요청 시 `-i` (include headers) 옵션을 사용하여 Set-Cookie 헤더를 확인합니다.

```bash
curl -i -X POST http://localhost:8080/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "dev@test.com",
    "password": "Password123!"
  }'
```

**응답 헤더 예시:**
```http
HTTP/1.1 200 OK
Set-Cookie: accessToken=eyJhbGciOiJSUzI1...; Path=/; HttpOnly; SameSite=Lax
Set-Cookie: refreshToken=d29a...; Path=/; HttpOnly; SameSite=Lax
...
```

위의 `Set-Cookie` 헤더에서 `accessToken` 값(eyJ...)을 복사합니다.

### 3.2. Authorization 헤더 사용

```bash
curl -X GET http://localhost:8080/meetings/123 \
  -H "Authorization: Bearer <COPY_PASTE_ACCESS_TOKEN>"
```

---

## 4. 게스트 로그인

게스트 로그인은 회원가입 없이 임시 토큰을 쿠키로 발급받습니다. Access Token은 4시간, Refresh Token은 1일 유효합니다.

```bash
# 쿠키 저장 방식
curl -c guest_cookies.txt -X POST http://localhost:8080/auth/v1/login/guest \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Guest User",
    "meetingId": "meeting-123"
  }'

# 서비스 호출
curl -b guest_cookies.txt http://localhost:8080/meetings/meeting-123/join
```

---

## 5. 토큰 갱신

Refresh Token이 유효한 경우 새로운 Access/Refresh 토큰을 발급받을 수 있습니다 (Token Rotation).

```bash
# 쿠키 파일에서 refreshToken 자동 사용
curl -c cookies.txt -b cookies.txt -X POST http://localhost:8080/auth/v1/refresh
```

---

## 6. 로그아웃

Access Token을 블랙리스트에 추가하고 쿠키를 삭제합니다.

```bash
curl -b cookies.txt -X POST http://localhost:8080/auth/v1/logout
```
