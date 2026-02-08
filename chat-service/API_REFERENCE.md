# Chat Service API Reference

실시간 채팅 및 메시지 관리를 담당하는 서비스입니다.

## 기본 정보 (Base Info)
- **Base URL**: `/chat` (Gateway 경유 시)
- **Port**: `8084`

## Endpoints

### 1. 내 정보 확인 (Check Auth)
인증된 사용자의 정보를 확인합니다.

- **URL**: `/me`
- **Method**: `GET`
- **Auth**: Required

#### Response
- **Status**: `200 OK`
- **Body**: `String`
```text
Hello from Chat Service! User ID: {userId}
```
