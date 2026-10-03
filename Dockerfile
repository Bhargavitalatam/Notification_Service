# Stage 1: Build application using Gradle
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app

# Copy configuration and cache dependencies
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true

# Copy source code and build jar (tests executed separately in CI/CD pipeline)
COPY src ./src
RUN gradle bootJar -x test --no-daemon

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
