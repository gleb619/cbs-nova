# T595 — Move ExplainReports to `cbs.nova.dsl.utils` package

Load and use skills `caveman` and `codegraph` on work start.

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

## Goal

Resolve TODO in `ExplainReports.java:17` — relocate `cbs.nova.dsl.model.ExplainReports` (a static-method-only
utility class, no state) into the existing `cbs.nova.dsl.utils` package, matching the codebase's established
util-package convention.

## Acceptance Criteria

- `ExplainReports` moved to `cbs.nova.dsl.utils.ExplainReports` (package + file path).
- All references updated: `ExplainReport.java`, `ExplainSupportTest.java`, `ExplainReportTest.java`, and any
  other caller found via `codegraph_explore`/grep.
- `@Deprecated` annotation and TODO comment removed from the class once relocated.
- Compiles; existing tests for `ExplainReports`/`ExplainReport` still pass.

## Tier

`backend`

## Files to create/modify

- `backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/model/ExplainReports.java` (move → `dsl-api/src/main/java/cbs/nova/dsl/utils/ExplainReports.java`)
- `backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/model/ExplainReport.java` (import update)
- `backend/dsl-platform/dsl-api/src/test/java/cbs/nova/dsl/ExplainSupportTest.java`
- `backend/dsl-platform/dsl-api/src/test/java/cbs/nova/dsl/model/ExplainReportTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl-api:test
backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test
```
