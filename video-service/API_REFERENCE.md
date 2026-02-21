# Video Service API Reference

화상 회의 방 생성, 관리 및 시그널링을 담당하는 서비스입니다.

## 기본 정보 (Base Info)
- **Base URL**: `/video` (Gateway 경유 시)
- **Port**: `8083`

## Endpoints

### 1. 내 정보 확인 (Check Auth)
인증된 사용자의 정보를 확인합니다.

- **URL**: `/videos/me`
- **Method**: `GET`
- **Auth**: Required

#### Response
- **Status**: `200 OK`
- **Body**: `String`
```text
Hello from Video Service! User ID: {userId}
```
