# Stage 1: Build JAR using Maven on Temurin JDK 21 Alpine
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime using Temurin JRE 21 Alpine
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Create non-root user and persistent storage directory
RUN addgroup -S homecloud && adduser -S homecloud -G homecloud \
    && mkdir -p /app/HomeCloudStorage \
    && chown -R homecloud:homecloud /app

USER homecloud:homecloud

# Copy compiled JAR from builder stage
COPY --from=builder --chown=homecloud:homecloud /build/target/*.jar /app/app.jar

ENV SERVER_PORT=8080 \
    SERVER_ADDRESS=0.0.0.0 \
    STORAGE_LOCATION=/app/HomeCloudStorage \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
