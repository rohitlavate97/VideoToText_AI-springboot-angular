# =============================================================================
# Stage 1: Build stage
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# =============================================================================
# Stage 2: Production Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="VideoToText AI Team"
LABEL description="VideoToText AI Spring Boot Backend"

WORKDIR /app

RUN apk update && apk upgrade && apk add --no-cache curl tzdata && rm -rf /var/cache/apk/*

RUN addgroup -S -g 1001 appgroup && \
    adduser -S -u 1001 -G appgroup appuser

COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

RUN mkdir -p /app/logs /app/config /app/temp && \
    chown -R appuser:appgroup /app

USER appuser

ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
