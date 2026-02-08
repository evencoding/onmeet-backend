# 로컬 개발 환경 로그인 테스트 가이드

이 프로젝트는 **쿠키(Cookie) 기반 인증**을 기본으로 사용합니다. 따라서 로컬 테스트 시에도 쿠키를 저장하고 전송하는 방식을 권장합니다.

## 1. 사전 준비 (Prerequisites)

`auth-service`가 정상적으로 실행되어 있어야 합니다.

- **Auth Service URL**: `http://localhost:8080/auth` (Gateway)

---

## 2. 쿠키 기반 테스트 (권장)

`curl`의 쿠키 저장(`-c`) 및 전송(`-b`) 옵션을 사용하면 브라우저와 동일한 환경을 시뮬레이션할 수 있습니다.

### 2.1. 로그인 및 쿠키 저장

로그인 성공 시 서버가 반환하는 `accessToken` 및 `refreshToken` 쿠키를 파일(`cookies.txt`)에 저장합니다.

```bash
# -c cookies.txt: 서버에서 받은 쿠키를 파일에 저장
curl -c cookies.txt -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "dev@test.com",
    "password": "Password123!"
  }'
```

**응답 확인:**
정상 로그인 시 `Login successful` 메시지가 반환되며, `cookies.txt` 파일이 생성됩니다.

### 2.2. 다른 서비스 호출 (쿠키 사용)

저장된 쿠키 파일을 사용하여 인증이 필요한 다른 서비스(예: Video, Chat)를 호출합니다.

```bash
# -b cookies.txt: 저장된 쿠키를 요청에 포함
curl -b cookies.txt -X GET http://localhost:8080/meetings/123
```

---

## 3. 헤더 기반 테스트 (개발자 편의용)

`auth-service` 및 각 서비스의 필터(`JwtAuthenticationFilter`)는 개발 편의를 위해 **Authorization 헤더** 방식도 지원하도록 구성되어 있습니다. 쿠키 처리가 번거로운 API 도구(일부 Postman 설정 등) 사용 시 유용합니다.

### 3.1. 토큰 추출

로그인 요청 시 `-i` (include headers) 옵션을 사용하여 헤더를 확인합니다.

```bash
curl -i -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "dev@test.com",
    "password": "Password123!"
  }'
```

**응답 헤더 예시:**
```http
HTTP/1.1 200 OK
Set-Cookie: accessToken=eyJhbGciOiJSUzI1...; Path=/; HttpOnly; ...
Set-Cookie: refreshToken=d29a...; Path=/; HttpOnly; ...
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

게스트 로그인 역시 쿠키를 반환하므로 동일하게 테스트할 수 있습니다.

```bash
# 쿠키 저장 방식
curl -c guest_cookies.txt -X POST http://localhost:8080/auth/guests/login \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Guest User",
    "meetingId": "meeting-123"
  }'

# 서비스 호출
curl -b guest_cookies.txt http://localhost:8080/meetings/meeting-123/join
```
