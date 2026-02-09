# Build Stage
FROM golang:1.23-alpine AS build
WORKDIR /app
COPY go.mod ./
COPY . .
RUN go mod tidy
RUN CGO_ENABLED=0 GOOS=linux go build -o file-service main.go

# Run Stage
FROM alpine:latest
WORKDIR /root/
COPY --from=build /app/file-service .
EXPOSE 8086
ENTRYPOINT ["./file-service"]
