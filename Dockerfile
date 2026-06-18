# syntax=docker/dockerfile:1

# ---------- Stage 1: build the bootable jar ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy build descriptors first so dependency resolution is cached
# independently of source changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now copy sources and build (tests run in CI, not in the image build).
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------- Stage 2: minimal runtime ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/build/libs/*.jar app.jar
USER spring

# Render injects the port via $PORT; the app reads it as server.port.
EXPOSE 8082

# Container-aware heap sizing; honour $JAVA_OPTS if provided.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 $JAVA_OPTS -jar /app/app.jar"]
