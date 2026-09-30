# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# Build stage: compiles the starter module and its project dependencies.
# The whole backend/ directory is copied because :starter depends on
# :dsl-api, :dsl, and :misc-codegen (the annotation processor must run at
# compile time), and they are wired as project dependencies.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /build

COPY backend/ ./

# The dsl-examples module applies the unpublished cbs.nova.dsl Gradle plugin,
# which breaks Gradle configuration in a clean container. dsl-examples is only
# a test dependency of :starter, so it is safe to exclude it from the build
# configuration here. The production boot jar is unaffected.
ENV GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=120000 -Dorg.gradle.internal.http.socketTimeout=120000"

# BuildKit cache mount for the Gradle user home so dependency resolution
# (`~/.gradle/caches/modules-2`, configuration cache, build cache) survives
# across rebuilds of this stage. The mount is scoped to this single RUN, so
# nothing leaks into the runtime stage or the final image layer.
RUN --mount=type=cache,target=/root/.gradle \
    sed -i "/include 'dsl-examples'/d" settings.gradle && \
    sed -i "/testImplementation project(':dsl-examples')/d" starter/build.gradle && \
    ./gradlew :starter:bootJar --no-daemon

# -----------------------------------------------------------------------------
# Runtime stage: minimal JRE image running the app as a non-root user.
# Only the fat jar from the build stage is copied; no source or build cache.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-jammy

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

COPY --from=build /build/starter/build/libs/starter-*.jar /app/app.jar

# curl https://repo1.maven.org/maven2/io/sentry/sentry-opentelemetry-agent/8.53.0/sentry-opentelemetry-agent-8.53.0.jar -o sentry-opentelemetry-agent-8.53.0.jar
ENV SENTRY_AUTO_INIT=false \
    JAVA_TOOL_OPTIONS_EXPERIMENT="-javaagent:sentry-opentelemetry-agent-8.53.0.jar" \
    # Container-aware JVM sizing: MaxRAMPercentage scales heap to the cgroup
    # memory limit; ExitOnOutOfMemoryError fails the process fast so the
    # orchestrator sees a non-zero exit instead of a wedged container.
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER appuser

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
