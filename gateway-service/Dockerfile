# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Gradle Wrapper 및 설정 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 의존성 먼저 다운로드하여 캐싱 최적화
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# 공통 모듈 및 Gateway 서비스 소스 복사
COPY common-security common-security
COPY gateway-service gateway-service

# 빌드 수행
RUN ./gradlew :gateway-service:build -x test --no-daemon

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/gateway-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
