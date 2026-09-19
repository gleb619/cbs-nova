# T597 — Migrate hardcoded Gradle deps to version catalog

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Eliminate hardcoded dependency strings in backend `build.gradle` files by moving them into
`backend/gradle/libs.versions.toml`, so all versions have a single source of truth.

Known offenders (from TODO sweep 2026-09-19):

| File | Dependency |
|---|---|
| `backend/dsl-starter/starter/build.gradle:69` | `com.knuddels:jtokkit:1.1.0` (has explicit TODO) |
| `backend/dsl-plugins/dsl-idea-plugin/build.gradle:28` | `junit:junit:4.13.2` |
| `backend/dsl-plugins/dsl-builder/build.gradle:13` | `org.gradle:gradle-tooling-api:9.4.1` |
| `backend/dsl-plugins/dsl-builder/build.gradle:19` | `org.springframework.boot:spring-boot-starter-webmvc-test` (no version — platform-managed, still should be cataloged) |

## Tier

`backend`

## Acceptance criteria

- [ ] All four dependencies above referenced via `libs.*` catalog accessors; hardcoded string coordinates removed.
- [ ] New entries added to `backend/gradle/libs.versions.toml` following existing naming convention (`kebab-case` aliases, version refs or `[versions]` entries).
- [ ] No other hardcoded `group:artifact:version` implementation/api/testImplementation strings remain in `backend/**` builds (sweep with grep; report any justified exceptions).
- [ ] `backend/dsl-starter/gradlew -p backend/dsl-starter :starter:dependencies --configuration runtimeClasspath -x test` resolves jtokkit from catalog.
- [ ] `backend/dsl-plugins/gradlew -p backend/dsl-plugins build -x test` green.
- [ ] `make lint` green (Spotless).
- [ ] TODO comment at `starter/build.gradle:68` removed.

## Out of scope

- `app/server` and `app/dsl` builds (separate app-level builds; hardcoded deps there are a separate task if desired).
- Dependency version upgrades — move coordinates as-is.

## Files to create/modify

- `backend/gradle/libs.versions.toml` (modify)
- `backend/dsl-starter/starter/build.gradle` (modify)
- `backend/dsl-plugins/dsl-idea-plugin/build.gradle` (modify)
- `backend/dsl-plugins/dsl-builder/build.gradle` (modify)

## Build/test commands

```bash
backend/dsl-starter/gradlew -p backend/dsl-starter :starter:compileJava -x test
backend/dsl-plugins/gradlew -p backend/dsl-plugins build -x test
make lint
```
