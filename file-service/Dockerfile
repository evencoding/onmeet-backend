# syntax=docker/dockerfile:1

# Build Stage
FROM golang:1.24-alpine AS build

# 빌드에 필요한 도구 설치
RUN apk add --no-cache git

WORKDIR /app

# 의존성 다운로드 (캐시 마운트로 재사용)
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

# 소스 복사 및 빌드 (캐시 마운트로 재빌드 속도 향상)
COPY . .
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -trimpath \
    -o file-service main.go

# Run Stage
FROM alpine:latest

# 보안 및 TLS 인증서
RUN apk --no-cache add ca-certificates tzdata

# 비root 사용자 생성
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# 애플리케이션 디렉토리
WORKDIR /app

# 빌드된 바이너리와 문서 복사
COPY --from=build /app/file-service .
COPY --from=build /app/docs ./docs

# 파일 소유권 변경
RUN chown -R appuser:appuser /app

# 비root 사용자로 실행
USER appuser

EXPOSE 8086

ENTRYPOINT ["./file-service"]
