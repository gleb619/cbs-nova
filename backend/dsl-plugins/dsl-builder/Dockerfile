# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# DSL Builder: single-stage image on `gradle:9-jdk25`.
#
# Runtime must be a JDK + Gradle distribution because the service drives the
# Gradle Tooling API (`GradleService`) to compile user DSL on demand. A bare
# JRE would not be able to fork a Gradle build. The same base is also used for
# the build phase so we have one toolchain across the image.
# -----------------------------------------------------------------------------
FROM gradle:9-jdk25

WORKDIR /build

# :dsl-plugins builds against the version catalog at backend/gradle/libs.versions.toml
# and shares convention scripts (java/test/code-style) + a central gradle.properties
# with the rest of backend/, so all three are copied to mirror the layout that
# :dsl-plugins/build.gradle expects.
COPY backend/dsl-plugins/ ./dsl-plugins/
COPY backend/gradle/ ./gradle/
COPY backend/gradle.properties ./gradle.properties

# Tune HTTP timeouts so initial Maven Central / plugin-portal resolves don't
# trip the daemon's default 30s socket timeout.
ENV GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=120000 -Dorg.gradle.internal.http.socketTimeout=120000"

# Drop the IntelliJ plugin subproject: it pulls in heavy IDE-specific
# dependencies and is irrelevant for the runtime jar.
RUN sed -i "/include 'dsl-idea-plugin'/d" dsl-plugins/settings.gradle && \
    sed -i "/include 'dsl-gradle-plugin'/d" dsl-plugins/settings.gradle && \
    gradle -p dsl-plugins :dsl-builder:bootJar --no-daemon --console=plain

# Copy the fat jar to a stable path and clean Gradle caches to slim the image.
# Spring Boot's bootJar emits `<artifact>-<version>.jar` (fat, runnable) plus a
# `<artifact>-<version>-plain.jar` (original classes). The plain one has no
# Main-Class, so we must exclude it explicitly.
RUN sh -c 'set -e; \
    fat=$(ls dsl-plugins/dsl-builder/build/libs/dsl-builder-*.jar | grep -v -- "-plain\.jar$" | head -1); \
    cp "$fat" /app.jar; \
    rm -rf ~/.gradle/caches/modules-2 ~/.gradle/caches/jars-* /root/.gradle/caches/transforms-*'

# Create non-root user for runtime.
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Workspace dir must exist and be writable by appuser (default points at
# /tmp/dsl-builder inside the container).
RUN mkdir -p /tmp/dsl-builder && chown -R appuser:appgroup /tmp/dsl-builder /app.jar

USER appuser

ENV CBS_DSL_BUILDER_WORKSPACE_DIR=/tmp/dsl-builder

EXPOSE 8091

ENTRYPOINT ["java", "-jar", "/app.jar"]
