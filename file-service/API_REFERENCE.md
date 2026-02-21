# File Service API Reference

파일 업로드 및 다운로드를 관리하는 서비스입니다.

## 기본 정보 (Base Info)
- **Base URL**: `/file` (또는 `/image`)
- **Port**: `8086`

## Endpoints

### 1. 헬스 체크 (Health Check)
서비스 상태를 확인합니다.

- **URL**: `/`
- **Method**: `GET`
- **Auth**: None

#### Response
- **Status**: `200 OK`
- **Body**: `String`
```text
File Service is operational
```
