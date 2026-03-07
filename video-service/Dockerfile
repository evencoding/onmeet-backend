# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Gradle Wrapper 및 설정 파일 복사
COPY --chmod=0755 gradlew .
COPY gradle gradle
# 의존성 및 소스 전체 복사
COPY . .

# 빌드 수행
RUN ./gradlew clean :video-service:build -x test --no-daemon



# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/video-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
