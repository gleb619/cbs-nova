# T600 — Replace hand-rolled frontmatter parser with flexmark YAML-front-matter ext

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Replace the hand-rolled frontmatter splitting in
`cbs.nova.dsl.explain.ExplainResourceFrontmatter`
(`backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/explain/ExplainResourceFrontmatter.java:22`,
TODO) with a library: `com.vladsch.flexmark:flexmark` + `flexmark-ext-yaml-front-matter`
(TODO names flexmark or commonmark as acceptable; commonmark has no frontmatter extension, so
flexmark is the fit).

Class is actively used despite `@Deprecated(forRemoval = true)`:
- `backend/dsl-platform/dsl-codegen/.../ExplainResourceGenerator.java:104`
- `backend/dsl-platform/dsl/src/main/java/cbs/nova/dsl/explain/ExplainResourceExplainer.java:27`
- 9-case test suite `ExplainResourceFrontmatterTest` pins current behavior.

Keep the public API (`Parsed(metadata, body)` record, static `parse`) unchanged so callers stay
untouched; swap internals. Preserve current lenient behaviors the tests pin (no-frontmatter →
empty map + unchanged source; unclosed block → treated as no frontmatter; body trimmed).

## Tier

`backend`

## Acceptance criteria

- [ ] Dependency added to `backend/gradle/libs.versions.toml` (coordinate via catalog — see T597
      convention) and to `dsl-api/build.gradle`.
- [ ] `ExplainResourceFrontmatter.parse` delegates to flexmark yaml-front-matter extension;
      hand-rolled line scanning removed; TODO comment removed.
- [ ] All 9 existing `ExplainResourceFrontmatterTest` cases pass unchanged (adjust only where
      flexmark is strictly more correct, e.g. quoted values — and then extend the test to pin the
      new behavior).
- [ ] `ExplainResourceGenerator` + `ExplainResourceExplainer` untouched and their tests green.
- [ ] `backend/dsl-platform/gradlew -p backend/dsl-platform test` green.
- [ ] `make lint` green.
- [ ] Decide and record: drop `@Deprecated(forRemoval = true)` once lib-backed (it was marked for
      removal because hand-rolled; if keeping deprecation, state why in javadoc).

## Out of scope

- CommonMark alternative (pick flexmark only — one library).
- Any change to explain markdown file format or generator templates.

## Files to create/modify

- `backend/gradle/libs.versions.toml` (modify)
- `backend/dsl-platform/dsl-api/build.gradle` (modify)
- `backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/explain/ExplainResourceFrontmatter.java` (modify)
- `backend/dsl-platform/dsl-api/src/test/java/cbs/nova/dsl/explain/ExplainResourceFrontmatterTest.java` (modify)

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl-api:test
backend/dsl-platform/gradlew -p backend/dsl-platform test
make lint
```
