# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Gradle Wrapper 및 설정 파일 복사
COPY --chmod=0755 gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 의존성 먼저 다운로드하여 캐싱 최적화
RUN ./gradlew dependencies --no-daemon

# 공통 모듈 및 Chat 서비스 소스 복사
COPY common-security common-security
COPY chat-service chat-service

# 빌드 수행
RUN ./gradlew :chat-service:build -x test --no-daemon

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/chat-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
