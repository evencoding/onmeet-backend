# AI Service API Reference

**Version**: v0.1.0
**Last Updated**: 2026-03-02
**Status**: Production

AI 관련 기능(회의록 요약, 분석 등)을 제공하는 서비스입니다.

## 기본 정보 (Base Info)
- **Base URL**: `/ai` (Gateway 경유 시)
- **Port**: `8082`

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
Hello from AI Service! User ID: {userId}
```
