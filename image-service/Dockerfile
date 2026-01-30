FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY image-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
