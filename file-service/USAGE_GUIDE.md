# File Service 사용 가이드

본 가이드는 OnMeet 프로젝트의 **File Service**를 효과적으로 활용하기 위한 방법을 설명합니다.

## 1. 개요
File Service는 모든 업로드 파일을 AWS S3에 안전하게 분리하여 저장하고, CloudFront를 통해 고속으로 배포합니다. 특히 **다형적 소유권(Polymorphic Ownership)** 시스템을 통해 개인, 팀, 회사 단위의 데이터 격리를 지원합니다.

## 2. 저장 구조 (S3 Key Convention)
파일은 다음 규칙에 따라 리소스를 계층화합니다:
`/{ownerType}/{ownerId}/{category}/{UUID_fileName}`

- **ownerType**: `USER`, `TEAM`, `COMPANY`, `SYSTEM`
- **ownerId**: 소유 주체의 고유 식별자
- **category**: 도메인별 분류 (포토, 녹음파일, 채팅파일 등)

## 3. 동기 업로드 vs 비동기 업로드

### 동기 업로드 (`/upload`)
- **언제 사용하나?**: 업로드 성공 후 즉시 파일 URL이 필요한 경우 (예: 프로필 사진 변경)
- **특징**: HTTP 응답으로 `FileResponseDto` 리스트를 즉시 반환하며, 모든 DB 저장이 완료된 상태임을 보장합니다.

### 비동기 업로드 (`/upload-async`)
- **언제 사용하나?**: 대용량 음성 녹음 파일 업로드, 다량의 배치 파일 처리 등
- **특징**:
  1. 클라이언트는 즉시 `202 Accepted` 응답을 받습니다.
  2. 실제 S3 업로드 및 DB 처리는 백그라운드 스레드에서 진행됩니다.
  3. 완료 시 지정된 **Kafka Topic**으로 파일 정보를 발행(Callback)합니다.

## 4. 비동기 콜백(Callback) 활용하기
비동기 업로드 완료 후 후속 작업(예: 음성 인식 시작, 문서 요약 등)을 위해 Kafka 이벤트를 구독해야 합니다.

- **Kafka Event Schema**:
  - `fileId`: DB 식별자
  - `fileName`: 저장된 파일명
  - `fileUrl`: 접근 가능한 CDN URL
  - `correlationId`: 업로드 요청 시 전달한 요청 추적 ID

## 5. Swagger 활용 (API 문서)
Swagger UI를 통해 웹 환경에서 직접 API를 테스트하고 정의를 확인할 수 있습니다.
- **URL**: `http://localhost:8083/swagger-ui.html` (Local 기준)

## 6. 주의 사항
- **인증**: 모든 요청에는 API Gateway를 통해 전달되는 `X-User-Id`가 필요합니다.
- **용량**: 비동기 업로드 시 서버 메모리 부하를 줄이기 위해 내부적으로 바이트 배열 복사 과정을 거치므로, 극도로 큰 파일은 S3 Pre-signed URL 방식을 별도로 고려할 수 있습니다.
