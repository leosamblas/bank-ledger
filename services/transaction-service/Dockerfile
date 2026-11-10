FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY services/gradlew services/settings.gradle services/build.gradle* ./services/
COPY services/gradle ./services/gradle
COPY services/platform-commons ./services/platform-commons
COPY services/transaction-service ./services/transaction-service

WORKDIR /workspace/services
RUN chmod +x gradlew && ./gradlew :transaction-service:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/services/transaction-service/build/libs/transaction-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "--enable-preview", "-XX:+UseZGC", "-jar", "app.jar"]

