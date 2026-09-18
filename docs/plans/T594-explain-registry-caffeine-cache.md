# T594 — ExplainResourceRegistry: Caffeine-backed lookup cache

Load and use skills `caveman` and `codegraph` on work start.

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

## Goal

Resolve TODO in `ExplainResourceRegistry.java:17` — replace the `@Deprecated(forRemoval=true)` raw
`ConcurrentHashMap` fields (`byName`, `byFilename`) with a Caffeine-backed cache/memoize, matching the
deprecation intent already marked on those fields.

## Acceptance Criteria

- `byName`/`byFilename` lookups backed by Caffeine cache (bounded or loading cache, project's existing
  Caffeine usage/config conventions followed if any exist elsewhere in the codebase).
- `@Deprecated(forRemoval=true)` annotations removed once fields are replaced.
- Public API of `ExplainResourceRegistry` (`init`, `register`, `findByName`, `findByFilename`,
  `describeByName`, `describeByFilename`, `names`, `filenames`) unchanged — no caller changes required.
- Existing tests for `ExplainResourceRegistry` (or callers) still pass.
- TODO comment removed.

## Tier

`backend`

## Files to create/modify

- `backend/dsl-platform/dsl/src/main/java/cbs/nova/dsl/explain/ExplainResourceRegistry.java`
- `backend/dsl-platform/dsl/build.gradle*` — add Caffeine dependency if not already present in this module.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl:test
backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test
```
