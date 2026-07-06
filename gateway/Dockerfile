# syntax=docker/dockerfile:1

# ----------------------------------------------------------------------------
# Build stage: compile the runnable jar from source using a Gradle image — Gradle
# is preinstalled (pulled from Docker Hub), so the image build doesn't download the
# Gradle distribution at build time. Reproducible from a clean clone.
# ----------------------------------------------------------------------------
FROM gradle:8.14.5-jdk21 AS build
WORKDIR /workspace

# Copy the build definition first. This layer only changes when the build files
# change, so Docker can cache the (slow) dependency download across code edits.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY config ./config
COPY gateway/build.gradle.kts ./gateway/build.gradle.kts

RUN gradle :gateway:dependencies --no-daemon -q || true

# Now the sources, then build only the bootable jar. Checks and tests run in CI,
# not in the image build, to keep image builds fast and focused.
COPY gateway/src ./gateway/src
RUN gradle :gateway:bootJar --no-daemon

# ----------------------------------------------------------------------------
# Runtime stage: small JRE, unprivileged user, just the jar.
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# curl backs the container healthcheck; the user keeps the process off root.
RUN apk add --no-cache curl \
 && adduser -D -u 10001 -s /sbin/nologin gatewise
USER gatewise

COPY --from=build /workspace/gateway/build/libs/gateway.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
