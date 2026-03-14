# File Service API Reference

**Version**: v0.2.0
**Last Updated**: 2026-03-14
**Status**: Production

File Service는 파일의 업로드, 조회, 삭제, CDN(CloudFront) 연동 및 기본 프로필 이미지 생성을 관리하는 마이크로서비스입니다.

**Tech Stack**: Go 1.24.0 (Gin Framework), PostgreSQL 16, AWS S3/CloudFront, Kafka

## 기본 정보

- **Base URL**: `/file` (Gateway 경유 시)
- **Protected Prefix**: `/file/v1` (SecurityMiddleware 적용)
- **Port**: `8086`
- **Database**: PostgreSQL 16 (JSONB 메타데이터 지원)
- **Storage**: AWS S3 + CloudFront CDN
- **Region**: ap-northeast-2 (Seoul)

## 공통 요청 헤더

모든 `/file/v1/*` 엔드포인트에 필수 헤더:

| Header | 필수 여부 | Description |
| :--- | :---: | :--- |
| `X-Gateway-Secret` | 필수 | Gateway 공유 시크릿. 누락 또는 불일치 시 `403 Forbidden` 반환 |
| `X-User-Id` | 권장 | 현재 사용자 ID (문자열 형태의 정수). 업로드/삭제 시 필요 |
| `X-User-Roles` | 선택 | 쉼표로 구분된 역할 목록 (예: `USER,MANAGER`) |

---

## 엔드포인트 목록

| Method | Path | 설명 | 응답 코드 |
| :--- | :--- | :--- | :--- |
| `POST` | `/file/v1/upload` | 파일 업로드 (동기) | 200 |
| `POST` | `/file/v1/upload-async` | 파일 업로드 (비동기) | 202 |
| `GET` | `/file/v1/{fileId}` | 파일 메타데이터 조회 | 200 |
| `DELETE` | `/file/v1/{fileId}` | 파일 삭제 (MANAGER/ADMIN 전용) | 204 |
| `DELETE` | `/file/v1/me/profile` | 내 프로필 이미지 전체 삭제 | 204 |
| `POST` | `/file/v1/profile/default` | 기본 프로필 이미지 생성 | 200 |
| `GET` | `/file/v1/render/{fileId}` | 파일 렌더링/다운로드 | 200 |
| `GET` | `/file/actuator/health` | 헬스 체크 (인증 불필요) | 200 |
| `GET` | `/file/swagger/*` | Swagger UI (인증 불필요) | 200 |

---

## 1. 파일 업로드 (동기)

여러 파일을 한 번에 업로드하고 저장된 메타데이터를 즉시 반환합니다.

- **Endpoint**: `POST /file/v1/upload`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `files` (필수) - 업로드할 파일 배열
  - `category` (선택) - 파일 카테고리 (예: `TEAM_PROFILE`, `MEETING_SUMMARY`)
  - `ownerType` (선택) - 소유자 유형: `USER` | `TEAM` | `COMPANY` | `MEETING`. 기본값: `USER`
  - `ownerId` (선택) - 소유자 ID. 기본값: X-User-Id 헤더 값
- **Response**: `200 OK` - `[]*FileMetadata` 배열

**허용 MIME 타입**: `image/jpeg`, `image/png`, `image/gif`, `image/webp`, `application/pdf`, `text/plain`

```bash
curl -X POST http://localhost:8086/file/v1/upload \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -H "X-User-Id: 42" \
  -F "files=@image.png" \
  -F "category=TEAM_PROFILE" \
  -F "ownerType=TEAM" \
  -F "ownerId=100"
```

---

## 2. 파일 업로드 (비동기)

파일 업로드를 비동기로 처리합니다. 즉시 `202 Accepted`를 반환하며, 업로드 완료 시 Kafka 이벤트를 발행합니다.

- **Endpoint**: `POST /file/v1/upload-async`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `files`, `category`, `ownerType`, `ownerId`: 동기 업로드와 동일
  - `callbackTopic` (선택) - Kafka 콜백 토픽. 기본값: `file-upload-events`
  - `correlationId` (선택) - 요청 추적 ID
- **Response**: `202 Accepted`

```json
{
  "message": "Batch file upload started asynchronously...",
  "fileCount": 3
}
```

**Kafka 이벤트 스키마** (`file-upload-events` 토픽):
```json
{
  "fileId": 123,
  "fileName": "550e8400-e29b-41d4-a716-446655440000.jpg",
  "fileUrl": "https://cdn.onmeet.cloud/USER/42/TEAM_PROFILE/550e8400.jpg",
  "uploaderId": 42,
  "timestamp": "2026-03-14T09:00:00Z",
  "status": "COMPLETED",
  "correlationId": "optional-tracking-id"
}
```

동시 처리 제한: 최대 10개 고루틴 (세마포어 제어)

---

## 3. 파일 메타데이터 조회

- **Endpoint**: `GET /file/v1/{fileId}`
- **Path Parameter**: `fileId` (uint)
- **Response**: `200 OK` - `FileMetadata` 객체

```bash
curl http://localhost:8086/file/v1/1 \
  -H "X-Gateway-Secret: YOUR_SECRET"
```

---

## 4. 파일 삭제

파일을 S3 및 DB에서 삭제합니다. **MANAGER 또는 ADMIN 역할**이 필요합니다.

- **Endpoint**: `DELETE /file/v1/{fileId}`
- **Path Parameter**: `fileId` (uint)
- **Authorization**: auth-service를 통해 MANAGER 또는 ADMIN 역할 검증
  - COMPANY 소유 파일: 요청자와 파일 소유 company가 동일해야 함
  - 그 외 파일: 요청자와 업로더가 같은 company 소속이어야 함
- **Response**: `204 No Content`

```bash
curl -X DELETE http://localhost:8086/file/v1/1 \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -H "X-User-Id: 42"
```

---

## 5. 내 프로필 이미지 전체 삭제

현재 사용자가 업로드한 모든 category=`profile` 파일을 삭제합니다.

- **Endpoint**: `DELETE /file/v1/me/profile`
- **요구 사항**: `X-User-Id` 헤더 필수 (없으면 `401 Unauthorized`)
- **Response**: `204 No Content`

```bash
curl -X DELETE http://localhost:8086/file/v1/me/profile \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -H "X-User-Id: 42"
```

---

## 6. 기본 프로필 이미지 생성

이름의 첫 글자와 지정 색상으로 SVG 프로필 이미지를 자동 생성하여 S3에 업로드합니다.

- **Endpoint**: `POST /file/v1/profile/default`
- **Content-Type**: `application/json`
- **Request Body**:

```json
{
  "name": "홍길동",
  "color": "#FF5733",
  "ownerType": "USER",
  "ownerId": "42"
}
```

| Field | Type | 필수 | Description |
| :--- | :--- | :---: | :--- |
| `name` | string | 필수 | 이름 (첫 글자를 SVG에 표시) |
| `color` | string | 선택 | 배경 hex 색상. 미입력 시 15가지 프리셋 중 랜덤 선택 |
| `ownerType` | string | 선택 | `USER` \| `TEAM` \| `COMPANY` \| `MEETING`. 기본값: `USER` |
| `ownerId` | string | 선택 | 소유자 ID. 기본값: X-User-Id 헤더 값 |

- **Response**: `200 OK` - `FileMetadata` 객체 (SVG 파일 메타데이터)

---

## 7. 파일 렌더링/다운로드

파일 이진 데이터를 직접 반환합니다. 브라우저 캐싱을 지원합니다.

- **Endpoint**: `GET /file/v1/render/{fileId}`
- **Path Parameter**: `fileId` (uint)
- **Response Headers**: `Cache-Control: public, max-age=3600`
- **Response**: `200 OK` - 파일 이진 데이터

**캐싱 전략**:
- 1MB 미만 파일: 서버 메모리에 1시간 TTL로 캐시
- 1MB 이상 파일: S3에서 직접 스트리밍

```bash
curl http://localhost:8086/file/v1/render/1 \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -o downloaded_file.jpg
```

---

## 데이터 모델 (FileMetadata)

`file_metadata` 테이블 스키마 및 응답 구조:

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | uint | 파일 고유 ID |
| `fileName` | string | S3 저장 파일명 (UUID 기반, 예: `550e8400-...jpg`) |
| `category` | string | 파일 카테고리 (예: `TEAM_PROFILE`, `MEETING_SUMMARY`) |
| `originalFileName` | string | 원본 파일명 |
| `s3Url` | string | CloudFront CDN URL |
| `fileSize` | int64 | 파일 크기 (Bytes) |
| `contentType` | string | MIME 타입 (예: `image/jpeg`) |
| `ownerType` | string | 소유 주체 유형: `USER` \| `TEAM` \| `COMPANY` \| `MEETING` |
| `ownerId` | string | 소유 주체 ID (숫자 문자열 또는 `SYSTEM`) |
| `uploaderId` | int64? | 업로드한 사용자 ID (nullable) |
| `extraInfo` | JSON | PostgreSQL JSONB - 확장 메타데이터 |
| `createdAt` | DateTime | 생성 일시 |
| `updatedAt` | DateTime | 수정 일시 |

**S3 Key 구조**: `{ownerType}/{ownerId}/{category}/{fileName}`
예: `USER/42/TEAM_PROFILE/550e8400-e29b-41d4-a716-446655440000.jpg`

---

## 에러 코드

### 에러 응답 형식
```json
{
  "code": "FILE_001",
  "status": 400,
  "message": "멀티파트 폼 데이터 파싱에 실패했습니다",
  "timestamp": 1710403200000
}
```

### 에러 코드 목록

| Code | HTTP Status | 설명 |
| :--- | :---: | :--- |
| `FILE_001` | 400 | 멀티파트 폼 파싱 실패 |
| `FILE_003` | 400 | 비동기 멀티파트 폼 파싱 실패 |
| `FILE_004` | 400 | fileId 형식 오류 (조회) |
| `FILE_005` | 404 | 파일을 찾을 수 없음 (조회) |
| `FILE_006` | 400 | fileId 형식 오류 (삭제) |
| `FILE_007` | 500 | 파일 삭제 실패 |
| `FILE_008` | 401 | X-User-Id 헤더 누락 |
| `FILE_009` | 500 | 프로필 삭제 실패 |
| `FILE_010` | 400 | 요청 JSON 파싱 실패 |
| `FILE_011` | 500 | 프로필 이미지 생성 실패 |
| `FILE_012` | 400 | fileId 형식 오류 (렌더링) |
| `FILE_013` | 404 | 파일 렌더링/다운로드 실패 |
| `FILE_014` | 403 | X-Gateway-Secret 누락 또는 불일치 |
| `FILE_016` | 500 | S3 업로드 실패 |
| `FILE_017` | 500 | 메타데이터 DB 저장 실패 |
| `FILE_019` | 500 | auth-service 권한 조회 실패 |
| `FILE_020` | 403 | MANAGER/ADMIN 역할 없음 |
| `FILE_021` | 403 | 다른 company 파일 접근 거부 |
| `FILE_022` | 500 | S3 삭제 실패 |
| `FILE_023` | 500 | DB 삭제 실패 |
| `FILE_024` | 500 | 프로필 목록 조회 실패 |
| `FILE_034` | 500 | S3 GetObject 오류 |
| `FILE_035` | 500 | 파일 읽기 오류 |
| `FILE_041` | 500 | DB 저장 실패 |
| `FILE_042` | 404 | DB 레코드 없음 |
| `FILE_043` | 500 | DB 삭제 실패 |
| `FILE_044` | 500 | DB 목록 조회 실패 |
| `FILE_045` | 500 | auth-service 요청 생성 실패 |
| `FILE_046` | 500 | auth-service 연결 실패 |
| `FILE_047` | 500 | auth-service 응답 오류 |
| `FILE_048` | 500 | auth-service 응답 파싱 실패 |
| `FILE_050` | 400 | ownerType 또는 ownerId 유효성 오류 |
| `FILE_051` | 400 | 허용되지 않는 MIME 타입 |
