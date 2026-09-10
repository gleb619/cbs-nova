# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# DSL Builder: single-stage image on `gradle:9-jdk25`.
#
# Runtime must be a JDK + Gradle distribution because the service drives the
# Gradle Tooling API (`GradleService`) to compile user DSL on demand. A bare
# JRE would not be able to fork a Gradle build. The same base is also used for
# the build phase so we have one toolchain across the image.
#
# Build phase publishes the DSL platform and the `cbs.nova.dsl` Gradle plugin
# into the image's Maven Local: every compile session scaffolds a fresh Gradle
# project that resolves `cbs.nova:*` artifacts and the plugin from
# `mavenLocal()`, so the repository must be seeded at image build time.
# -----------------------------------------------------------------------------
FROM gradle:9-jdk25

WORKDIR /build

# :dsl-plugins builds against the version catalog at backend/gradle/libs.versions.toml
# and shares convention scripts (java/test/code-style) + a central gradle.properties
# with the rest of backend/, so all three are copied to mirror the layout that
# the sub-builds expect. :dsl-platform and :starter are copied so their modules
# can be published to Maven Local for the runtime scaffold builds.
COPY backend/dsl-platform/ ./dsl-platform/
COPY backend/dsl-plugins/ ./dsl-plugins/
COPY backend/dsl-starter/ ./dsl-starter/
COPY backend/gradle/ ./gradle/
COPY backend/gradle.properties ./gradle.properties

# Tune HTTP timeouts so initial Maven Central / plugin-portal resolves don't
# trip the daemon's default 30s socket timeout.
ENV GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=120000 -Dorg.gradle.internal.http.socketTimeout=120000"

# Drop the IntelliJ plugin subproject: it pulls in heavy IDE-specific
# dependencies and is irrelevant for the runtime jar. dsl-gradle-plugin must
# stay — runtime scaffold builds apply it — so it is published to Maven Local
# together with the platform and the starter (DSL sources may compile against
# starter helper model classes), then the service fat jar is built.
RUN sed -i "/include 'dsl-idea-plugin'/d" dsl-plugins/settings.gradle && \
    gradle -p dsl-platform publishToMavenLocal -x test --no-daemon --console=plain && \
    gradle -p dsl-plugins :dsl-gradle-plugin:publishToMavenLocal -x test --no-daemon --console=plain && \
    gradle -p dsl-starter :starter:publishToMavenLocal -x test -x :starter:generateGitProperties --no-daemon --console=plain && \
    gradle -p dsl-plugins :dsl-builder:bootJar --no-daemon --console=plain

# Copy the fat jar to a stable path and clean Gradle caches to slim the image.
# Spring Boot's bootJar emits `<artifact>-<version>.jar` (fat, runnable) plus a
# `<artifact>-<version>-plain.jar` (original classes). The plain one has no
# Main-Class, so we must exclude it explicitly. The Maven Local repository
# (~/.m2) is intentionally kept: runtime compile sessions resolve cbs.nova
# artifacts from it.
RUN sh -c 'set -e; \
    fat=$(ls dsl-plugins/dsl-builder/build/libs/dsl-builder-*.jar | grep -v -- "-plain\.jar$" | head -1); \
    cp "$fat" /app.jar; \
    rm -rf /build ~/.gradle/caches/modules-2 ~/.gradle/caches/jars-* ~/.gradle/caches/transforms-*'

# Runtime user. APP_UID/APP_GID default to the common desktop-user ids and are
# overridden from compose with the host user's ids so files created on the
# mounted data volume are owned by the host user. The gradle base image
# already occupies uid 1000 ("gradle"), so it is removed first; when the
# requested ids are free a dedicated appuser is created.
ARG APP_UID=1000
ARG APP_GID=1000
# The build phase above ran as root, so Maven Local landed in /root/.m2;
# relocate it into the runtime user's home where the scaffold builds look.
RUN set -e; \
    if getent passwd "${APP_UID}" >/dev/null; then \
      userdel -r "$(getent passwd "${APP_UID}" | cut -d: -f1)"; \
    fi; \
    if ! getent group "${APP_GID}" >/dev/null; then \
      groupadd -g "${APP_GID}" appgroup; \
    fi; \
    if ! getent passwd "${APP_UID}" >/dev/null; then \
      useradd --uid "${APP_UID}" --gid "${APP_GID}" --create-home appuser; \
    fi; \
    APP_HOME=$(getent passwd "${APP_UID}" | cut -d: -f6); \
    mv /root/.m2 "$APP_HOME/.m2"; \
    mkdir -p /var/lib/dsl-builder/workspace /var/lib/dsl-builder/repo /var/lib/dsl-builder/worktrees; \
    chown -R "${APP_UID}:${APP_GID}" "$APP_HOME/.m2" /var/lib/dsl-builder

USER ${APP_UID}:${APP_GID}

# Workspace, persistent repo clone and per-compile worktrees all live under a
# single data root so one volume mount covers everything Gradle and git touch.
ENV CBS_DSL_BUILDER_WORKSPACE_DIR=/var/lib/dsl-builder/workspace \
    CBS_DSL_BUILDER_GIT_REPOSITORY_DIR=/var/lib/dsl-builder/repo \
    CBS_DSL_BUILDER_GIT_WORKTREES_DIR=/var/lib/dsl-builder/worktrees \
    GRADLE_USER_HOME=/var/lib/dsl-builder/gradle

EXPOSE 8091

ENTRYPOINT ["java", "-jar", "/app.jar"]
