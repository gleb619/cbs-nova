# T599 — Migrate `DslDescriptor` class to a record

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Convert `cbs.nova.dsl.DslDescriptor`
(`backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/DslDescriptor.java`, TODO at line 13)
from a Lombok `@Builder @Getter @Accessors(fluent=true)` final class to a Java record, resolving
the `//TODO: change to a record`.

Shape is already record-compatible: final class, all fields `final`, fluent accessors.
Keep `@Builder` (Lombok supports records) so the ~16 existing `.builder()` call sites stay
source-compatible; record component accessors (`objectDescriptor()`, `hasSideEffects()`, …)
replace the fluent getters with identical names. Delegating methods `name()/type()/description()/
inputType()/outputType()` stay as explicit members.

## Tier

`backend`

## Acceptance criteria

- [ ] `DslDescriptor` is a record; TODO removed; class-level `@Deprecated` retained.
- [ ] `@Builder` (+ `@Builder.Default` on `hasSideEffects`, `parameters`) preserved — no builder
      call-site changes required anywhere in `backend/**`.
- [ ] Delegating accessors (`name()`, `type()`, `description()`, `inputType()`, `outputType()`)
      compile unchanged; no caller edits outside `DslDescriptor.java` except where nullness or
      equals/hashCode semantics surface in tests.
- [ ] `backend/dsl-platform/gradlew -p backend/dsl-platform build -x test` compiles.
- [ ] `backend/dsl-platform/gradlew -p backend/dsl-platform test` green.
- [ ] `backend/dsl-starter/gradlew -p backend/dsl-starter :starter:test` green (starter consumes
      dsl-api).
- [ ] `make lint` green.

## Notes / risks

- Record adds `equals`/`hashCode`/`toString` — if any test asserted identity semantics, update
  the test, not the record.
- `@Nullable` components (`taskQueue`, `version`, timeouts) keep their jspecify annotations on
  record components.
- Keep `@NonNull` on the `objectDescriptor` component; drop the redundant inner `@Getter`.

## Out of scope

- Removing `@Deprecated` or migrating `ObjectDescriptor` (T588 covers its `default` modifier).
- Any serialization-format changes — verify Jackson (Jackson 3) round-trips the record in
  existing tests before finishing.

## Files to create/modify

- `backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/DslDescriptor.java` (modify)
- Possibly a nullness/equals test under `backend/dsl-platform/dsl-api/src/test/java/` (modify/add)

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl-api:test
backend/dsl-platform/gradlew -p backend/dsl-platform test
backend/dsl-starter/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Retry Notes (attempt 1, 2026-09-22)

`at` agent (cline) got stuck in a broken tool-call loop (170+ near-identical `echo` probe commands
in one iteration, no real progress) and left `DslDescriptor.java` with a half-finished edit
(Lombok annotations stripped but not yet converted to record syntax — did not compile). Discarded
the partial edit and reset the file. No evidence the record-conversion approach itself is wrong;
this looks like an agent-specific execution glitch, not a task-design problem. Retry with a
different agent.

## Retry Notes (attempt 2, 2026-09-22)

`minimax-m3` (pi) executed the record conversion correctly. Build verification surfaced a Lombok
1.18.38 + JDK 25 + record + `@Builder.Default` incompatibility — Lombok 1.18.38 produces a
malformed intermediate source for record components annotated `@Builder.Default` under JDK 25's
compact-source-file rules ("compact source file should not have package declaration"). Upgraded
Lombok to **1.18.46** (explicit JDK 25 support per upstream changelog; cache-warm). With
1.18.46, the record form compiles; the `@Builder.Default` annotations remain on the components
as required by the plan acceptance criteria, and the canonical constructor coerces `null`
`parameters` to `List.of()` so the builder default semantics for `parameters` are preserved
at runtime.

Pre-existing on `main` (unrelated to this task): a handful of dsl / dsl-codegen / dsl-starter
tests fail on Lombok 1.18.38 + JDK 25 (e.g. `defaultExplainFallsBackToEmptyMarkdownWhenResourceMissing`,
`CodeWriterTest` tmp-path flake, `misc-codegen` spotless formatting). None of these are caused
by the record conversion or the Lombok bump — verified by re-running the suite on `main` with
the same result set. Spotless also reports pre-existing formatting violations in
`Executable.java`, `ExplainResource.java`, `ExplainReport.java`, and `HelperSpiProcessor.java`;
none include `DslDescriptor.java`.

Jackson 3 round-trip (`RunDefinitionHashTest`, `DescriptorsTest`) passes after the change —
the explicit `DslDescriptorSerializer` reads accessor methods that already match the record
component names.
