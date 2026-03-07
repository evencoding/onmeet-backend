# Email Service API Reference

**Version**: v0.1.0
**Last Updated**: 2026-03-02
**Status**: Production

이메일 발송 기능을 담당하는 서비스입니다. (Kafka Consumer 기반 동작 위주)
REST API는 헬스 체크용으로 주로 사용됩니다.

## 기본 정보 (Base Info)
- **Port**: `8087`

## Endpoints

### 1. 헬스 체크 (Health Check)
서비스 상태를 확인합니다.

- **URL**: `/health`
- **Method**: `GET`
- **Auth**: None

#### Response
- **Status**: `200 OK`
- **Body**: `String`
```text
Email Service is running
```
