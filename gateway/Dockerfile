# syntax=docker/dockerfile:1

# ----------------------------------------------------------------------------
# Build stage: compile the runnable jar straight from source, so the image is
# reproducible from a clean clone with no host build artifacts involved.
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy the build definition first. This layer only changes when the build files
# change, so Docker can cache the (slow) dependency download across code edits.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY config ./config
COPY gateway/build.gradle.kts ./gateway/build.gradle.kts

RUN chmod +x ./gradlew && ./gradlew :gateway:dependencies --no-daemon -q || true

# Now the sources, then build only the bootable jar. Checks and tests run in CI,
# not in the image build, to keep image builds fast and focused.
COPY gateway/src ./gateway/src
RUN ./gradlew :gateway:bootJar --no-daemon

# ----------------------------------------------------------------------------
# Runtime stage: small JRE, unprivileged user, just the jar.
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# curl backs the container healthcheck; the user keeps the process off root.
RUN apk add --no-cache curl \
 && adduser -D -u 10001 -s /sbin/nologin auvex
USER auvex

COPY --from=build /workspace/gateway/build/libs/gateway.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
