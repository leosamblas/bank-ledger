FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY services/gradlew services/settings.gradle services/build.gradle* ./services/
COPY services/gradle ./services/gradle
COPY services/platform-commons ./services/platform-commons
COPY services/notification-service ./services/notification-service

WORKDIR /workspace/services
RUN chmod +x gradlew && ./gradlew :notification-service:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/services/notification-service/build/libs/notification-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8087

ENTRYPOINT ["java", "--enable-preview", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "-XX:+ExitOnOutOfMemoryError", "-Dreactor.schedulers.defaultBoundedElasticOnVirtualThreads=true", "-jar", "app.jar"]
