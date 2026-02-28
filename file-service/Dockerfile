# Build Stage
FROM golang:1.24-alpine AS build
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -o file-service main.go

# Run Stage
FROM alpine:latest
WORKDIR /root/
COPY --from=build /app/file-service .
COPY --from=build /app/docs ./docs
EXPOSE 8086
ENTRYPOINT ["./file-service"]
