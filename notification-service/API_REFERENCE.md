# Notification Service API Reference

**Version**: v0.1.0
**Last Updated**: 2026-03-02
**Status**: Production

실시간 알림(SSE) 및 알림 이력 관리를 담당하는 서비스입니다.

## 기본 정보 (Base Info)
- **Base URL**: `/notification` (Gateway 경유 시)
- **Port**: `8085`

## Endpoints

### 1. 내 정보 확인 (Check Auth)
인증된 사용자의 정보를 확인합니다.

- **URL**: `/notification/me`
- **Method**: `GET`
- **Auth**: Required

#### Response
- **Status**: `200 OK`
- **Body**: `String`
```text
Hello from Notification Service! User ID: {userId}
```
