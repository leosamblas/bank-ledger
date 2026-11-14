FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY services/gradlew services/settings.gradle services/build.gradle* ./services/
COPY services/gradle ./services/gradle
COPY services/platform-commons ./services/platform-commons
COPY services/ledger-service ./services/ledger-service

WORKDIR /workspace/services
RUN chmod +x gradlew && ./gradlew :ledger-service:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/services/ledger-service/build/libs/ledger-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "--enable-preview", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

