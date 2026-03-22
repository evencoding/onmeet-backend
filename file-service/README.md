# file-service

OnMeet 플랫폼의 파일 관리 마이크로서비스입니다.
파일 업로드/다운로드, S3 저장, CloudFront CDN 연동, 프로필 이미지 자동 생성을 담당합니다.

- **포트**: 8086
- **언어**: Go 1.24.0
- **데이터베이스**: PostgreSQL 16

---

## 기술 스택

| 항목 | 기술 |
| :--- | :--- |
| 언어 | Go 1.24.0 |
| 웹 프레임워크 | Gin |
| ORM | GORM (gorm.io/gorm) |
| 데이터베이스 드라이버 | gorm.io/driver/postgres |
| AWS SDK | github.com/aws/aws-sdk-go-v2 |
| S3 클라이언트 | github.com/aws/aws-sdk-go-v2/service/s3 |
| 파일 스토리지 | AWS S3 + CloudFront CDN |
| 메시지 브로커 | Apache Kafka (segmentio/kafka-go) |
| UUID 생성 | github.com/google/uuid |
| 인메모리 캐시 | github.com/patrickmn/go-cache |
| API 문서 | Swagger (swaggo/swag + swaggo/gin-swagger) |

---

## 주요 기능

- **파일 업로드 (동기)**: 즉시 S3 업로드 및 PostgreSQL 메타데이터 저장, 결과 반환
- **파일 업로드 (비동기)**: 202 즉시 응답 후 백그라운드 처리, Kafka로 완료 이벤트 발행
- **파일 렌더링/다운로드**: 인메모리 캐싱(1MB 미만) 및 S3 직접 스트리밍(1MB 이상)
- **파일 삭제**: MANAGER/ADMIN 역할 기반 접근 제어, auth-service 연동 권한 검증
- **기본 프로필 이미지 생성**: 이름 첫 글자와 색상으로 SVG 자동 생성 후 S3 업로드
- **내 프로필 삭제**: 사용자 본인의 모든 프로필 이미지 일괄 삭제
- **보안**: Gateway 공유 시크릿 검증, MIME 타입 sniffing, S3 Key 인젝션 방지

---

## 프로젝트 구조

```
file-service/
├── main.go                     # 서비스 진입점, 의존성 주입, 라우터 설정, 그레이스풀 셧다운
├── go.mod                      # Go 모듈 의존성
├── go.sum                      # 의존성 체크섬
├── docs/                       # swag init으로 생성된 Swagger 문서
│   ├── docs.go
│   ├── swagger.json
│   └── swagger.yaml
└── internal/
    ├── client/
    │   └── auth_client.go      # auth-service HTTP 클라이언트 (권한 조회)
    ├── config/
    │   └── config.go           # 환경 변수 로딩
    ├── handler/
    │   └── file_handler.go     # HTTP 핸들러 (Gin 라우트 처리)
    ├── middleware/
    │   ├── error_handler.go    # 전역 에러 응답 통일 미들웨어
    │   └── security.go         # X-Gateway-Secret 검증 미들웨어
    ├── model/
    │   ├── errors.go           # AppError, ErrorCode 정의 (FILE_001~FILE_051)
    │   ├── file_metadata.go    # FileMetadata GORM 엔티티
    │   └── request.go          # 요청 DTO (GenerateProfileImageRequest 등)
    ├── repository/
    │   └── file_repository.go  # PostgreSQL CRUD (GORM 기반)
    └── service/
        ├── event_producer.go   # Kafka 이벤트 발행 (segmentio/kafka-go)
        ├── file_service.go     # 핵심 비즈니스 로직
        └── s3_service.go       # AWS S3 연동 (업로드/삭제/다운로드)
```

### 패키지별 역할

| 패키지 | 역할 |
| :--- | :--- |
| `handler` | HTTP 요청 파싱, 파라미터 추출, 서비스 호출, 응답 반환 |
| `service` | 비즈니스 로직 (업로드 프로세스, 캐싱, 권한 검증, 비동기 처리) |
| `repository` | PostgreSQL CRUD 연산 (GORM) |
| `model` | 엔티티, 에러 코드, 요청/응답 DTO 정의 |
| `middleware` | 인증 검증 (SecurityMiddleware), 에러 응답 표준화 |
| `client` | auth-service와의 HTTP 통신 (권한 조회) |
| `config` | 환경 변수 기반 설정 로딩 |

---

## 환경 설정

| 환경 변수 | 기본값 | 필수 | 설명 |
| :--- | :--- | :---: | :--- |
| `SERVER_PORT` | `8086` | - | HTTP 서버 포트 |
| `DB_URL` | `host=postgres-file user=postgres password=root dbname=file_db port=5432 sslmode=disable` | - | PostgreSQL 연결 문자열 |
| `GATEWAY_SHARED_SECRET` | (없음) | **필수** | Gateway 공유 시크릿. 미설정 시 서비스 시작 불가 |
| `AWS_REGION` | `ap-northeast-2` | - | AWS 리전 |
| `S3_BUCKET_NAME` | (없음) | **필수** | S3 버킷 이름 |
| `S3_ENDPOINT` | (없음) | - | 커스텀 S3 엔드포인트 (MinIO 등 로컬 개발용) |
| `CLOUDFRONT_DOMAIN` | (없음) | **필수** | CloudFront 도메인 (파일 URL 생성에 사용) |
| `KAFKA_BROKERS` | `kafka:9092` | - | Kafka 브로커 주소 |
| `AUTH_SERVICE_URL` | `http://auth-service:8081` | - | auth-service URL (권한 검증 API 호출) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8080` | - | CORS 허용 Origin |

---

## 빌드 및 실행

개별 서비스 실행 시 .env 파일의 환경변수가 주입되지 않으므로, Docker Compose를 통해 실행한다.

### 빌드

```bash
cd file-service

# 바이너리 빌드
go build -o file-service main.go
```

### 실행 (Docker Compose)

```bash
# 전체 서비스 시작
docker compose up -d

# file-service만 시작 (인프라 포함)
docker compose up -d file-service
```

### 테스트

```bash
cd file-service

# 전체 테스트
go test ./...

# 커버리지 포함
go test -cover ./...

# HTML 커버리지 리포트
go test -coverprofile=coverage.out ./... && go tool cover -html=coverage.out
```

### Swagger 문서 생성

```bash
cd file-service
swag init
```

생성 후 `http://localhost:8086/file/swagger/index.html`에서 확인 가능합니다.

---

## API 엔드포인트

| Method | Path | 인증 | 설명 |
| :--- | :--- | :---: | :--- |
| `POST` | `/file/v1/upload` | 필요 | 파일 업로드 (동기) |
| `POST` | `/file/v1/upload-async` | 필요 | 파일 업로드 (비동기 + Kafka 콜백) |
| `GET` | `/file/v1/{fileId}` | 필요 | 파일 메타데이터 조회 |
| `DELETE` | `/file/v1/{fileId}` | 필요 (MANAGER/ADMIN) | 파일 삭제 |
| `DELETE` | `/file/v1/me/profile` | 필요 | 내 프로필 이미지 전체 삭제 |
| `POST` | `/file/v1/profile/default` | 필요 | 기본 SVG 프로필 이미지 생성 |
| `GET` | `/file/v1/render/{fileId}` | 필요 | 파일 렌더링/다운로드 |
| `GET` | `/file/actuator/health` | 불필요 | 헬스 체크 |
| `GET` | `/file/swagger/*` | 불필요 | Swagger UI |

상세 스펙: [API_REFERENCE.md](./API_REFERENCE.md)

---

## 아키텍처

### S3 Key 구조

```
{ownerType}/{ownerId}/{category}/{UUID_fileName}
예: USER/42/TEAM_PROFILE/550e8400-e29b-41d4-a716-446655440000.jpg
```

### 파일 업로드 흐름 (동기)

```
클라이언트 → SecurityMiddleware (X-Gateway-Secret 검증)
           → FileHandler.Upload()
           → FileService.UploadFiles()
               → MIME 타입 검증 (sniffing)
               → S3 업로드
               → PostgreSQL 메타데이터 저장
               → (DB 실패 시) S3 롤백
           → []*FileMetadata 반환
```

### 파일 업로드 흐름 (비동기)

```
클라이언트 → FileHandler.UploadAsync() → 202 즉시 반환
                                        → 고루틴 (세마포어 max 10)
                                            → S3 업로드 + DB 저장
                                            → Kafka 이벤트 발행
```

### 파일 렌더링 흐름

```
클라이언트 → FileHandler.RenderFile()
           → FileService.RenderFile()
               → 캐시 확인 (1시간 TTL)
               → (미스) DB 조회 → S3 GetObject
               → 1MB 미만: 메모리 캐시 후 반환
               → 1MB 이상: S3 직접 스트리밍
```

### 서비스 연동

- **auth-service**: MANAGER/ADMIN 권한 검증 (`GET /users/internal/{userId}/permissions`)
- **Kafka**: 비동기 업로드 완료 이벤트 발행 (`file-upload-events` 토픽)
- **AWS S3**: 파일 저장소 (MinIO 호환)
- **CloudFront**: CDN URL 생성

---

## 관련 문서

- [API_REFERENCE.md](./API_REFERENCE.md) - 전체 API 엔드포인트 스펙 및 에러 코드
- [USAGE_GUIDE.md](./USAGE_GUIDE.md) - 서비스 활용 가이드 (업로드 전략, 캐싱, 콜백)
- [GO_MIGRATION_GUIDE.md](./GO_MIGRATION_GUIDE.md) - Kotlin에서 Go로의 마이그레이션 가이드
