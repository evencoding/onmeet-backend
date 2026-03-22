# File Service Go 마이그레이션 가이드

`file-service`가 기존 Kotlin/Spring Boot에서 Go(Gin) 기반으로 마이그레이션되었습니다.
이 문서에서는 변경된 내용, 빌드 방식, 환경 변수 설정 및 테스트 방법을 안내합니다.

## 주요 변경 사항

| 항목 | 변경 전 (Kotlin/Spring Boot) | 변경 후 (Go/Gin) |
| :--- | :--- | :--- |
| 언어 | Kotlin | Go 1.24.0 |
| 프레임워크 | Spring Boot 3.3.5 | Gin, GORM, AWS SDK v2 |
| API 경로 | `/api/v1/files/*` | `/file/v1/*` |
| 빌드 도구 | Gradle + Jib | Go build + Docker Multi-stage |
| 설정 방식 | `application.yml` (SPRING_ 접두사) | 환경 변수 (표준 명명 규칙) |
| 데이터베이스 | MySQL | PostgreSQL 16 |
| ORM | Spring Data JPA (Hibernate) | GORM |
| DI 컨테이너 | Spring Framework | 수동 의존성 주입 (main.go) |
| 공통 모듈 | `onmeet-common` (Kotlin) 공유 | 내부 재구현 (`SecurityMiddleware` 등) |
| 테스트 | JUnit 5 / MockK | Go testing + testify |

## 추가된 엔드포인트

Go 마이그레이션 과정에서 다음 엔드포인트가 신규 추가되었습니다:

| Method | Path | 설명 |
| :--- | :--- | :--- |
| `DELETE` | `/file/v1/me/profile` | 내 프로필 이미지 전체 삭제 |
| `POST` | `/file/v1/profile/default` | 기본 SVG 프로필 이미지 자동 생성 |
| `GET` | `/file/v1/render/{fileId}` | 파일 렌더링/다운로드 (캐싱 지원) |

## 빌드 및 실행 방법

### 1. 로컬 개발 환경 (Go 1.24.0 이상 필요)

```bash
cd file-service
go mod tidy
go run main.go
```

### 2. Swagger 문서 재생성

```bash
cd file-service
swag init
```

생성된 docs는 `docs/` 디렉토리에 저장되며, Swagger UI는 `http://localhost:8086/file/swagger/index.html`에서 확인할 수 있습니다.

### 3. Docker를 이용한 실행

```bash
docker-compose up --build file-service
```

`Dockerfile.go`를 사용하며, Alpine 리눅스 기반의 경량 이미지를 생성합니다.

## 환경 변수

| 변수명 | 기본값 | 필수 여부 | 설명 |
| :--- | :--- | :---: | :--- |
| `SERVER_PORT` | `8086` | 선택 | HTTP 서버 포트 |
| `DB_URL` | `host=postgres-file user=postgres password=root dbname=file_db port=5432 sslmode=disable` | 선택 | PostgreSQL 연결 문자열 |
| `GATEWAY_SHARED_SECRET` | (없음) | **필수** | Gateway 공유 시크릿. 미설정 시 서비스 시작 불가 |
| `AWS_REGION` | `ap-northeast-2` | 선택 | AWS 리전 |
| `S3_BUCKET_NAME` | (없음) | **필수** | S3 버킷 이름 |
| `S3_ENDPOINT` | (없음) | 선택 | 커스텀 S3 엔드포인트 (MinIO 등 로컬 개발용) |
| `CLOUDFRONT_DOMAIN` | (없음) | **필수** | CloudFront 도메인 (파일 URL 생성에 사용) |
| `KAFKA_BROKERS` | `kafka:9092` | 선택 | Kafka 브로커 주소 |
| `AUTH_SERVICE_URL` | `http://auth-service:8081` | 선택 | auth-service URL (권한 검증용) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8080` | 선택 | CORS 허용 Origin |

## 테스트 방법

### 1. 단위 테스트 실행

```bash
cd file-service

# 전체 테스트
go test ./...

# 패키지별 테스트
go test ./internal/service/...
go test ./internal/handler/...
go test ./internal/repository/...
go test ./internal/middleware/...
go test ./internal/client/...

# 커버리지 포함
go test -cover ./...
go test -coverprofile=coverage.out ./... && go tool cover -html=coverage.out
```

### 2. HTTP API 테스트 예시

**파일 업로드 (동기)**
```bash
curl -X POST http://localhost:8086/file/v1/upload \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -H "X-User-Id: 42" \
  -F "files=@image.png" \
  -F "category=TEAM_PROFILE" \
  -F "ownerType=TEAM" \
  -F "ownerId=100"
```

**파일 업로드 (비동기)**
```bash
curl -X POST http://localhost:8086/file/v1/upload-async \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -H "X-User-Id: 42" \
  -F "files=@recording.mp3" \
  -F "category=MEETING_SUMMARY" \
  -F "callbackTopic=ai-process-events" \
  -F "correlationId=meeting-123"
```

**기본 프로필 이미지 생성**
```bash
curl -X POST http://localhost:8086/file/v1/profile/default \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -H "X-User-Id: 42" \
  -H "Content-Type: application/json" \
  -d '{"name": "홍길동", "ownerType": "USER", "ownerId": "42"}'
```

**파일 렌더링**
```bash
curl http://localhost:8086/file/v1/render/1 \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -o output_file.jpg
```

## 주의 사항

- **공통 모듈 분리**: Go 서비스는 `onmeet-common` (Kotlin) 모듈을 공유할 수 없으므로, SecurityMiddleware 등 보안 로직이 `internal/middleware/` 내부에 재구현되어 있습니다.
- **MinIO 로컬 개발**: `S3_ENDPOINT` 환경 변수 설정 시 path-style URL을 사용하는 MinIO 호환 모드로 동작합니다.
- **대용량 파일**: 비동기 업로드 시 멀티파트 데이터를 고루틴 실행 전 바이트 배열로 복사합니다. 극도로 큰 파일은 S3 Pre-signed URL 방식을 별도로 고려하십시오.
- **동시성 제어**: 비동기 업로드는 세마포어를 통해 최대 10개 고루틴으로 제한됩니다.
