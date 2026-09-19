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
