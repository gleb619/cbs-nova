# T524 — Relocate `DslEventRepositoryTest` + `DslRunsMigrationTest` to `integrationTest`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

Move two `@Testcontainers`/`@Deprecated` Postgres test classes out of the `test` source set into
the existing `integrationTest` source set, per their own TODOs (`move to integrationTest folder`).

## Current state

- `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/persistence/DslEventRepositoryTest.java`
  — `// TODO: move to integrationTest folder`, `@Deprecated`, live Postgres testcontainer test
  pinning the V7 migration JSONB contract.
- `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/persistence/DslRunsMigrationTest.java`
  — `//TODO: move to integrationTest folder`, `@Deprecated`, live Postgres testcontainer test
  pinning migration schema.
- `backend/dsl-starter/starter/src/integrationTest/{java,resources}` source set already exists
  (used by other Testcontainers-based tests) — matches exactly what both TODOs ask for. No new
  Gradle wiring needed.
- Both classes currently run in the fast `test` task, pulling a Postgres container into the
  default unit-test run — slows `:starter:test` and violates the module's existing
  test/integrationTest split norm.

## Approach

1. `git mv` both files from `src/test/java/cbs/nova/starter/persistence/` to
   `src/integrationTest/java/cbs/nova/starter/persistence/` (package unchanged).
2. Drop the `// TODO: move to integrationTest folder` comment (relocation done) — keep
   `@Deprecated` if the classes are still deprecated for other reasons (verify each case first).
3. Verify no import/classpath breakage (`integrationTest` sourceSet already has
   `postgresql`/`testcontainers` deps per existing files there — confirm, don't assume).
4. Confirm `:starter:test` no longer starts a Postgres container for these two classes;
   `:starter:integrationTest` (or equivalent Gradle task) picks them up and passes.

## Acceptance criteria

- [ ] Both TODOs removed.
- [ ] Both classes compile and pass under `integrationTest`, package unchanged.
- [ ] `:starter:test` green and no longer references either class.
- [ ] `make lint` passes.

## Files to create/modify (best guess)

- Move: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/persistence/DslEventRepositoryTest.java`
  → `backend/dsl-starter/starter/src/integrationTest/java/cbs/nova/starter/persistence/DslEventRepositoryTest.java`
- Move: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/persistence/DslRunsMigrationTest.java`
  → `backend/dsl-starter/starter/src/integrationTest/java/cbs/nova/starter/persistence/DslRunsMigrationTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:integrationTest
make lint
```

## Out of scope

- Un-deprecating the classes, rewriting test logic, changing migration coverage,
  other `@Deprecated` TODO groups (T513/T514/T518/etc).
