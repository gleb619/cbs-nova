# T531 — Unit spec for `DslDefinitionHistoryService`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`DslDefinitionHistoryService` (155 L, starter module) has zero dedicated test file — only indirect
coverage via `DslDraftResourceTest`/`DslDefinitionBundleResourceTest` (handler-level, exercises the
HTTP surface, not this class's edge cases directly). Same gap pattern already fixed by T501/T502/
T530. The class also contains a path-traversal guard (`safeHistoryDir` throws on escape) that
deserves a direct, explicit test rather than relying on handler tests to happen to exercise it.

## Current state

`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/service/DslDefinitionHistoryService.java`:

- `snapshotBeforePublish(dir, name)` — copies current published JSON into a timestamp-named file
  under the history dir, then prunes; no-ops (with warn log) if nothing published yet or on any
  I/O exception.
- `list(dir, name)` — lists history entries newest-first by parsed timestamp; skips malformed
  filenames (non-numeric prefix) via `toEntry`'s `Optional.empty()` path; empty list if the
  history dir doesn't exist.
- `readEntry(dir, name, timestamp)` — rejects a timestamp not matching
  `WORKBENCH_HISTORY_TIMESTAMP_PATTERN` before touching the filesystem; `Optional.empty()` if
  missing or unparsable.
- `readPublished(dir, name)` — reads the current published JSON; `Optional.empty()` if missing or
  unparsable.
- `prune(historyDir)` — keeps only `dslProperties.drafts().historyLimit()` newest entries
  (lexicographic filename sort — relies on the fixed-width millis-timestamp naming); `limit <= 0`
  disables pruning entirely.
- `safeHistoryDir`/`safeFileName` — path-traversal guard: `safeFileName` strips to
  `[A-Za-z0-9._-]`, `safeHistoryDir` throws `IllegalArgumentException` if the resolved path escapes
  the history root.

## Approach

1. Use a tmp dir per test (JUnit `@TempDir`), real `ObjectMapper` (or the project's standard test
   double — check `DslDraftResourceTest`/similar for the idiom already in use), fake/minimal
   `DslProperties` with a configurable `historyLimit`.
2. `snapshotBeforePublish`: no published file → no-op, no history dir created; published file
   exists → snapshot copied, appears in `list()`.
3. `list`: empty when dir absent; newest-first ordering; malformed filename (non-numeric) skipped
   without throwing.
4. `readEntry`: invalid timestamp pattern rejected before filesystem touch (verify via a
   non-existent dir + non-matching timestamp → still `Optional.empty()`, not an exception);
   missing file → empty; valid round-trip.
5. `readPublished`: missing → empty; valid round-trip.
6. `prune`: `historyLimit` N → only N newest kept after repeated snapshots; `historyLimit <= 0` →
   unlimited (no deletions).
7. Path-traversal: a `name` containing `../`-style segments — confirm `safeFileName` neutralizes it
   (sanitized to underscores) rather than reaching `safeHistoryDir`'s throw path in practice; if a
   path can still reach the throw (e.g. via a crafted `dir` rather than `name`), test that
   `IllegalArgumentException` directly. Verify the actual reachable attack surface before writing
   the assertion — don't assert a path that can't occur.

## Acceptance criteria

- [ ] New `DslDefinitionHistoryServiceTest` covering all methods above.
- [ ] Path-safety guard explicitly tested.
- [ ] No production code changes (test-only).
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/service/DslDefinitionHistoryServiceTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- `RunDefinitionHash` (separate row if picked up later), `DslDraftHandler` (consumer, untouched),
  `dsl-builder`'s `DefinitionHistoryService` (different class, different module, already
  addressed in T493).
