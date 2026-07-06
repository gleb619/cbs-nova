# T79 — ExternalCallTracker unit tests

## Goal
Add unit tests for `ExternalCallTracker` and `ExternalCallListener` covering `record()` with/without an instance, thread-local tracking lifecycle, listener invocation, type normalization, and global count aggregation. Locks the external-call observability contract used by explain/runner reports.

## Tier
backend

## Files to create / modify
- Create: `backend/starter/src/test/java/cbs/nova/starter/ExternalCallTrackerTest.java`
- Read only: `backend/starter/src/main/java/cbs/nova/starter/ExternalCallTracker.java`, `backend/starter/src/main/java/cbs/nova/starter/ExternalCallListener.java`

## Acceptance criteria
- `record()` writes to active thread-local container when no instance exists.
- `recordCall()` merges thread-local calls, increments global counts, and notifies registered listeners.
- `normalizeType()` maps common aliases (`jdbc`, `rest`, `kafka`, `file`, `grpc`, `api`) to standard categories.
- `startTracking`/`stopTracking` isolate call collections per thread.
- `resetGlobalCounts()` clears aggregated counters.
- `./gradlew :starter:test --tests '*ExternalCallTrackerTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test --tests '*ExternalCallTrackerTest*'
```
