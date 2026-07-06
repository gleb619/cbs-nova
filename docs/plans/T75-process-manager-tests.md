# T75 — Process manager and runner tests

**Tier:** backend
**Status:** Backlog
**Owner:** loop

## Goal

`ProcessManager` and `DefaultProcessRunner` currently have no direct test coverage beyond `RunnerTest` exercising the runner indirectly. Add focused unit tests for process dispatch, `EXPLAIN` mode metadata enrichment, and compensation error wrapping so the runtime core is fully specified.

## Acceptance Criteria

- [ ] `backend/dsl/src/test/java/cbs/nova/dsl/ProcessManagerTest.java` created.
- [ ] Tests prove `ProcessManager.execute` dispatches to the runner for a registered process and returns `DslEntityNotFoundException` for unknown names.
- [ ] Tests prove `ProcessManager.contains`/`find`/`names` delegate to the registry.
- [ ] Extend `backend/dsl/src/test/java/cbs/nova/dsl/RunnerTest.java` (or new `DefaultProcessRunnerTest.java`) with tests for `EXPLAIN` mode metadata and compensation logic throwing `DslCompensationException`.
- [ ] `./gradlew spotlessApply && ./gradlew :dsl:test` passes.

## Files to Create / Modify

- `backend/dsl/src/test/java/cbs/nova/dsl/ProcessManagerTest.java` — new.
- `backend/dsl/src/test/java/cbs/nova/dsl/RunnerTest.java` or `DefaultProcessRunnerTest.java` — extend.

## Build / Test Commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test
```
