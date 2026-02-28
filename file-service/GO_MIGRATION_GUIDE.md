# File Service Go 마이그레이션 가이드

`file-service`가 기존 Kotlin/Spring Boot에서 Go(Gin) 기반으로 마이그레이션되었습니다. 이 문서에서는 변경된 빌드 방식과 테스트 방법을 안내합니다.

## ⚡ 주요 변경 사항

1.  **언어 및 프레임워크**: Kotlin (Spring Boot) -> Go (Gin, GORM, AWS SDK v2)
2.  **API 엔드포인트 구조**:
    *   기존: `/api/v1/files/upload` 등
    *   변경: `/file/upload`, `/file/upload-async`, `/file/{fileId}` (사용자 요청에 따라 `/api/v1` 제거)
3.  **빌드 도구**: Gradle -> Go Build & Docker Multi-stage Build

## 🛠 빌드 및 실행 방법

### 1. 로컬 개발 환경 설정
로컬에서 빌드하려면 Go 1.22 이상이 설치되어 있어야 합니다.
```bash
cd file-service
go mod tidy
go run main.go
```

### 2. Docker를 이용한 실행
`docker-compose`를 통해 즉시 실행할 수 있습니다.
```bash
docker-compose up --build file-service
```
*참고: `Dockerfile.go`를 사용하여 최적화된 Alpine 리눅스 기반의 가벼운 이미지를 생성합니다.*

## 🧪 테스트 방법

### 1. HTTP API 테스트 (curl 예시)

**파일 업로드**
```bash
curl -X POST http://localhost:8086/file/upload \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -F "category=profile" \
  -F "files=@your_image.png"
```

**비동기 파일 업로드**
```bash
curl -X POST http://localhost:8086/file/upload-async \
  -H "X-Gateway-Secret: YOUR_SECRET" \
  -F "category=chat" \
  -F "files=@data.zip" \
  -F "callbackTopic=my-callback-topic"
```

### 2. 단위 테스트 실행
Go의 내장 테스트 도구를 사용합니다.
```bash
go test ./internal/service/...
```

## ⚠️ 주의 사항

- **공통 모듈**: Go 서비스는 `onmeet-common` (Kotlin) 모듈을 공유할 수 없으므로, 보안 로직(`SecurityMiddleware`)이 내부적으로 재구현되었습니다.
- **환경 변수**: `SPRING_` 접두사가 붙은 설정 대신 `DB_URL`, `SERVER_PORT` 등의 표준 환경 변수명을 사용합니다.
