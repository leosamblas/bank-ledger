FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY services/gradlew services/settings.gradle services/build.gradle* ./services/
COPY services/gradle ./services/gradle
COPY services/platform-commons ./services/platform-commons
COPY services/account-service ./services/account-service

WORKDIR /workspace/services
RUN chmod +x gradlew && ./gradlew :account-service:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/services/account-service/build/libs/account-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "--enable-preview", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
