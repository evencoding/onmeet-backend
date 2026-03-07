# File Service API Reference

**Version**: v0.1.0
**Last Updated**: 2026-03-02
**Status**: Production

File Service는 파일의 업로드, 조회, 삭제 및 CDN(CloudFront) 연동을 관리하는 마이크로서비스입니다.

## 기본 정보
- **Base URL**: `/api/v1/files`
- **Default Port**: `8083` (Docker Compose 기준)
- **CDN**: `${CLOUDFRONT_DOMAIN}`

## 1. 파일 업로드 (동기)
여러 파일을 한 번에 업로드하고 저장된 정보를 반환받습니다.

- **Endpoint**: `POST /upload`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `files`: `MultipartFile[]` (Required) - 업로드할 파일 리스트
  - `category`: `String` (Required) - 파일 카테고리 (예: profile, audio, chat)
  - `ownerType`: `String` (Optional) - 소유자 유형 (`USER`, `TEAM`, `COMPANY`, `SYSTEM`). 기본값: `USER`
  - `ownerId`: `String` (Optional) - 소유자 ID. 기본값: 현재 인증된 사용자 ID
- **Response**: `List<FileResponseDto>`

## 2. 파일 업로드 (비동기)
대용량 파일이나 다량의 파일을 비동기로 처리하며, 즉시 접수 응답을 받습니다. 결과는 Kafka를 통해 콜백됩니다.

- **Endpoint**: `POST /upload-async`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `files`, `category`, `ownerType`, `ownerId`: 위와 동일
  - `callbackTopic`: `String` (Optional) - 결과를 수신할 Kafka 토픽. 기본값: `file-upload-events`
  - `correlationId`: `String` (Optional) - 결과 매칭을 위한 임의 ID
- **Response**: `202 Accepted`
  ```json
  {
    "message": "Batch file upload started asynchronously...",
    "fileCount": 3,
    "callbackTopic": "file-upload-events"
  }
  ```

## 3. 파일 정보 조회
- **Endpoint**: `GET /{fileId}`
- **Response**: `FileResponseDto`

## 4. 파일 삭제
- **Endpoint**: `DELETE /{fileId}`
- **Response**: `204 No Content`

## 데이터 모델 (FileResponseDto)
| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `Long` | 파일 고유 ID |
| `fileName` | `String` | S3에 저장된 UUID 파일명 |
| `category` | `String` | 파일 카테고리 |
| `originalFileName`| `String` | 원본 파일명 |
| `s3Url` | `String` | CloudFront 접근 URL |
| `fileSize` | `Long` | 파일 크기 (Bytes) |
| `contentType` | `String` | MIME Type |
| `ownerType` | `String` | 소유 주체 유형 |
| `ownerId` | `String` | 소유 주체 ID |
| `uploaderId` | `Long?` | 업로드한 사용자 ID |
| `createdAt` | `DateTime`| 생성 일시 |
