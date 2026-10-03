# Multi-stage Dockerfile for Notification Service
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Copy gradle wrapper and configuration
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# Grant execution permissions
RUN chmod +x ./gradlew

# Copy source code and build production jar
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Minimal, secure production runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for container health check
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Copy built artifact
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
