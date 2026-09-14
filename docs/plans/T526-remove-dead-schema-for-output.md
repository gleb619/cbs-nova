# T526 — Delete dead `DslIntrospectionService.schemaForOutput`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`DslIntrospectionService.java:219` TODO: `remove`. The private method `schemaForOutput(Class<?>)`
is `@Deprecated(forRemoval = true)` and has zero callers anywhere in the repo (verified via grep —
only occurrence is its own declaration). Delete it.

## Current state

```java
// TODO: remove
@Deprecated(forRemoval = true)
private Map<String, Object> schemaForOutput(Class<?> type) {
  return jsonSchemaGenerator.generateSchema(type);
}
```

No other reference in `backend/` (main or test sources). `schemaForInput` (the sibling, live,
used by `inputSchema(...)`) stays untouched.

## Approach

1. Confirm zero callers again at execution time (codegraph_callers / grep) — repo may have moved
   on since this stub was written.
2. Delete the method.
3. Run `:starter:test` — expect no change (method was unreferenced, dead).

## Acceptance criteria

- [ ] TODO removed (method deleted).
- [ ] `jsonSchemaGenerator` field still used elsewhere in the class (don't orphan the field).
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- Modify: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/service/DslIntrospectionService.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- `schemaForInput` or any other method in the class, `JacksonJsonSchemaGenerator` changes.
