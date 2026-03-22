# File Service 사용 가이드

본 가이드는 OnMeet 프로젝트의 **File Service**를 효과적으로 활용하기 위한 방법을 설명합니다.

## 1. 개요

File Service는 모든 업로드 파일을 AWS S3에 안전하게 저장하고, CloudFront를 통해 빠르게 배포합니다.
**다형적 소유권(Polymorphic Ownership)** 시스템을 통해 개인, 팀, 회사, 미팅 단위의 데이터 격리를 지원하며,
기본 프로필 이미지 자동 생성 및 파일 렌더링/캐싱 기능을 제공합니다.

## 2. 인증 방식

모든 `/file/v1/*` 요청에는 `X-Gateway-Secret` 헤더가 필수입니다.
이 헤더는 API Gateway가 자동으로 주입하며, 직접 서비스에 접근하는 경우 설정된 공유 시크릿을 사용해야 합니다.

- 누락 또는 불일치 시: `403 Forbidden` (`FILE_014`)
- 상수 시간 비교(constant-time comparison)를 사용하여 타이밍 공격을 방지합니다.

## 3. 저장 구조 (S3 Key Convention)

파일은 다음 규칙에 따라 S3에 계층적으로 저장됩니다:

```
{ownerType}/{ownerId}/{category}/{UUID_fileName}
```

**예시**: `USER/42/TEAM_PROFILE/550e8400-e29b-41d4-a716-446655440000.jpg`

- **ownerType**: `USER` | `TEAM` | `COMPANY` | `MEETING`
- **ownerId**: 소유 주체의 숫자 ID (또는 `SYSTEM`)
- **category**: 도메인별 분류 (예: `TEAM_PROFILE`, `MEETING_SUMMARY`)

## 4. 허용 파일 형식

보안을 위해 실제 파일 내용을 검사(MIME sniffing)합니다. 확장자가 아닌 파일 바이트를 기준으로 검증합니다.

허용 MIME 타입: `image/jpeg`, `image/png`, `image/gif`, `image/webp`, `application/pdf`, `text/plain`

## 5. 동기 업로드 vs 비동기 업로드

### 동기 업로드 (`POST /file/v1/upload`)

- **사용 시점**: 업로드 성공 후 즉시 파일 URL이 필요한 경우 (예: 프로필 사진 변경)
- **특징**: HTTP 응답으로 `[]*FileMetadata` 리스트를 즉시 반환하며, 모든 S3 저장 및 DB 저장이 완료된 상태임을 보장합니다.
- **원자성 보장**: DB 저장 실패 시 S3 업로드를 자동으로 롤백합니다.

### 비동기 업로드 (`POST /file/v1/upload-async`)

- **사용 시점**: 대용량 음성 녹음 파일 업로드, 다량의 배치 파일 처리 등
- **특징**:
  1. 클라이언트는 즉시 `202 Accepted` 응답을 받습니다.
  2. 실제 S3 업로드 및 DB 저장은 백그라운드 고루틴에서 진행됩니다.
  3. 완료 시 지정된 Kafka 토픽으로 파일 정보를 발행합니다.
  4. 동시 처리는 최대 10개 고루틴으로 제한됩니다.

## 6. 비동기 콜백(Callback) 활용하기

비동기 업로드 완료 후 후속 작업(음성 인식 시작, 문서 요약 등)을 위해 Kafka 이벤트를 구독합니다.

**기본 토픽**: `file-upload-events` (`callbackTopic` 파라미터로 커스텀 가능)

**Kafka 이벤트 스키마**:
```json
{
  "fileId": 123,
  "fileName": "550e8400-e29b-41d4-a716-446655440000.mp3",
  "fileUrl": "https://cdn.onmeet.cloud/MEETING/5/MEETING_SUMMARY/550e8400.mp3",
  "uploaderId": 42,
  "timestamp": "2026-03-14T09:00:00Z",
  "status": "COMPLETED",
  "correlationId": "meeting-session-xyz"
}
```

## 7. 기본 프로필 이미지 생성

별도 이미지 파일 없이 이름의 첫 글자와 색상으로 SVG 프로필 이미지를 자동 생성합니다.

```bash
curl -X POST http://localhost:8086/file/v1/profile/default \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -H "X-User-Id: 42" \
  -H "Content-Type: application/json" \
  -d '{"name": "홍길동", "ownerType": "USER", "ownerId": "42"}'
```

- `color` 미입력 시 15가지 프리셋 색상 중 랜덤 선택
- 생성된 SVG는 `category=profile`로 저장되며 `FileMetadata`를 반환합니다.

## 8. 파일 렌더링/다운로드

파일 이진 데이터를 직접 스트리밍합니다. 브라우저 렌더링 및 다운로드에 모두 활용 가능합니다.

```
GET /file/v1/render/{fileId}
```

**캐싱 전략**:
- 1MB 미만 파일: 서버 메모리에 1시간 TTL로 캐시 (10분마다 만료 정리)
- 1MB 이상 파일: S3에서 직접 스트리밍 (메모리 효율 우선)
- 응답 헤더: `Cache-Control: public, max-age=3600`

## 9. 파일 삭제 권한

파일 삭제(`DELETE /file/v1/{fileId}`)에는 MANAGER 또는 ADMIN 역할이 필요합니다.
auth-service를 통해 실시간으로 권한을 검증하며, 다른 company 소속 파일은 삭제할 수 없습니다.

## 10. Swagger API 문서

로컬 실행 시 Swagger UI로 API를 직접 테스트할 수 있습니다.

- **URL**: `http://localhost:8086/file/swagger/index.html`

Swagger 문서 재생성이 필요한 경우:
```bash
cd file-service
swag init
```
