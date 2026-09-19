# T606 — Version catalogs for `app/server` and `app/dsl` builds

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Follow-up to T597 (which cataloged `backend/**` builds and explicitly out-scoped `app/`).
`app/server` and `app/dsl` are two independent Gradle builds, each with its own wrapper and
settings, and every dependency hardcoded in `build.gradle`. Versions currently match
`backend/gradle/libs.versions.toml` by hand-copy (`temporal-sdk:1.27.0`, `avaje-jsonb:3.4`,
`junit:5.12.2`, `assertj:3.27.3`, spring starters, h2) — silent drift waiting to happen.

Task: add a `gradle/libs.versions.toml` to each app build and migrate all hardcoded coordinates
to `libs.*` accessors. Copy version numbers from `backend/gradle/libs.versions.toml` where the
artifact is shared (temporal, avaje, junit, assertj) so both stay textually aligned; do NOT
attempt a cross-build catalog share (includeBuild magic) — keep it dumb and local.

## Tier

`backend` (Gradle builds under `app/`)

## Acceptance criteria

- [ ] `app/dsl/gradle/libs.versions.toml` + `app/server/gradle/libs.versions.toml` created,
      aliases following the backend catalog's kebab-case convention.
- [ ] Zero hardcoded `group:artifact:version` strings left in either `build.gradle` (snapshot
      `cbs.nova:*:0.0.1-SNAPSHOT` project deps may stay inline OR become catalog entries —
      executor picks, stays consistent across both files).
- [ ] Shared-artifact versions byte-identical to `backend/gradle/libs.versions.toml`.
- [ ] `app/dsl/gradlew -p app/dsl build -x test` green.
- [ ] `app/server/gradlew -p app/server build -x test` green (needs platform published to
      Maven Local first: `backend/dsl-platform/gradlew -p backend/dsl-platform
      publishToMavenLocal -x test`).
- [ ] `make lint` green.

## Notes

- `app/*/gradle/` dirs already exist (wrapper) — toml goes next to `wrapper/`.
- Gradle 9.4.1 wrappers: catalog auto-loaded from `gradle/libs.versions.toml`, no settings
  change needed.

## Out of scope

- Version upgrades (move as-is).
- Cross-build catalog sharing / includeBuild refactor.

## Files to create/modify

- `app/dsl/gradle/libs.versions.toml` (create), `app/dsl/build.gradle` (modify)
- `app/server/gradle/libs.versions.toml` (create), `app/server/build.gradle` (modify)

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test
app/dsl/gradlew -p app/dsl build -x test
app/server/gradlew -p app/server build -x test
make lint
```
