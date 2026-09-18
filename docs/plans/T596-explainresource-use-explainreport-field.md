# T596 — ExplainResource: back name/description/content with an ExplainReport field

Load and use skills `caveman` and `codegraph` on work start.

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

## Goal

Resolve TODO in `ExplainResource.java:10` — collapse the standalone `name`/`description`/`content`
fields into a single `ExplainReport` field (`ExplainReport{name, description, mermaid, children}` in
`cbs.nova.dsl.model`), keeping `filename` separate since `ExplainReport` has no filename concept.
`ExplainResource.content` maps to `ExplainReport.mermaid` (the long-form markdown body).

## Acceptance Criteria

- `ExplainResource` record becomes `ExplainResource(ExplainReport report, String filename)` (or
  equivalent), removing the duplicated `name`/`description`/`content` fields.
- `name()`/`description()`/`content()` accessors either delegate to `report()` (compat shim) or all
  call sites are updated — pick whichever keeps the diff smallest given actual usage.
- `ExplainResourceProvider.resource()` default method updated to build the new shape.
- `@Deprecated` annotation and TODO comment removed once resolved.
- All 8 known call sites compile and pass: `ExplainResourceProvider.java`, `ExplainResourceRegistry.java`,
  `GlobalManager.java`, `FunctionBuilderTest.java`, `GlobalManagerExplainResourceTest.java`,
  `ProcessBuilderTest.java`, `TransactionBuilderTest.java` (verify full list via `codegraph_explore`
  first — index may have shifted since this stub was written).

## Tier

`backend`

## Files to create/modify

- `backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/explain/ExplainResource.java`
- `backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/explain/ExplainResourceProvider.java`
- `backend/dsl-platform/dsl/src/main/java/cbs/nova/dsl/explain/ExplainResourceRegistry.java`
- `backend/dsl-platform/dsl/src/main/java/cbs/nova/dsl/GlobalManager.java`
- Test files under `backend/dsl-platform/dsl/src/test/java/cbs/nova/dsl/` that construct `ExplainResource`.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl-api:test :dsl:test
backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test
```
