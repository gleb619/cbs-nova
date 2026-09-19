# T601 — Testcontainer for `dsl-builder` in versioning integration test

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Resolve the TODO at
`backend/dsl-starter/starter/src/integrationTest/resources/dsl-versioning-v2/VersionProbeDsl.java:9`:

```
//TODO: instead of use a compiler from source files, add a testcontainer for a `dsl-builder` module
```

`DslVersioningIntegrationTest.inFlightWorkflowKeepsUsingOriginalDslVersionAfterReload` triggers
`DslReloadHandler.reloadDefinitions()` from `sourceDir` — which today compiles via
`compileSources()`: remote `dsl-builder` when `builderClient()` resolves, else falls back to
in-process `JavaSourceCompiler` (forbidden per its own TODO / T570). Stand up the `dsl-builder`
module inside a Testcontainer so the reload path exercises the real remote compilation e2e and
the javac fallback is no longer needed for this test.

Test class already uses `@Testcontainers` — infra precedent in place.

## Tier

`backend`

## Acceptance criteria

- [ ] dsl-builder module runnable in a container (distroless/slim JRE base, or a
      `gradle:-jdk25` bootstrap — executor picks the lightest working option; document choice in
      plan execution notes).
- [ ] `DslVersioningIntegrationTest` wires `builderClient` to the containerized dsl-builder
      (host/port via `@Container` + dynamic property), reload goes through remote compile.
- [ ] In-process `JavaSourceCompiler` fallback NOT hit in this test (assert or verify by
      logging/mock).
- [ ] Existing assertions unchanged: v1 in-flight workflow keeps v1 result; reloaded version is
      v2; second run uses v2.
- [ ] Test skipped gracefully when Docker unavailable (Testcontainers `@Testcontainers(disabledWithoutDocker = true)`).
- [ ] `backend/dsl-starter/gradlew -p backend/dsl-starter :starter:integrationTest` green (with
      Docker up).
- [ ] TODO comment removed from `VersionProbeDsl.java`.

## Out of scope

- Removing `JavaSourceCompiler` itself (T570) — this task only makes the integration test not
  depend on it. Note in T601's completion message that T570's fallback-removal in
  `DslReloadHandler` is now unblocked.

## Files to create/modify

- `backend/dsl-plugins/dsl-builder/` (modify — add distribution/installDist task if missing)
- `backend/dsl-starter/starter/src/integrationTest/java/cbs/nova/dsl/example/integration/DslVersioningIntegrationTest.java` (modify)
- `backend/dsl-starter/starter/src/integrationTest/resources/dsl-versioning-v2/VersionProbeDsl.java` (modify — drop TODO)

## Build/test commands

```bash
backend/dsl-plugins/gradlew -p backend/dsl-plugins :dsl-builder:installDist
backend/dsl-starter/gradlew -p backend/dsl-starter :starter:integrationTest --tests '*DslVersioningIntegrationTest*'
make lint
```
