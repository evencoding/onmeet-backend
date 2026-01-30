FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /project
COPY . .
RUN chmod +x gradlew
# Skip tests to speed up build
RUN ./gradlew :ai-service:bootJar -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /project/ai-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]