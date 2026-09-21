# T634 — Builder describe(): plain DslDescriptor, not Supplier

## Goal

Replace the `describe(Supplier<DslDescriptor>)` builder API with a plain
`describe(DslDescriptor)` across DSL builders (`FunctionBuilder`, `TransactionBuilder`, and any
sibling with the same pattern — check `ProcessBuilder`/`HelperBuilder` via
`codegraph_search describe`). Remove the TODO at `FunctionBuilder.java:107`.

## Why

`FunctionBuilder.java:107` TODO: "redo from supplier to just simple descriptor". The supplier
buys laziness nobody uses — `build()` calls `effectiveDescriptor(...).get()` eagerly anyway.
Supplier param complicates every call site (`describe(() -> custom)`).

## Acceptance criteria

- [ ] `describe(DslDescriptor)` overload added; supplier variant deprecated-forRemoval or removed
      outright if all in-repo call sites migrate (prefer removal — this is a young API).
- [ ] All builders with the supplier pattern migrated consistently (Function, Transaction,
      Process/Helper if present).
- [ ] Call sites migrated: `FunctionBuilderTest`, `TransactionBuilderTest`, `GlobalManagerTest`,
      any examples (`grep '\.describe(() ->'` after change must be empty).
- [ ] Default descriptor path (`effectiveDescriptor`) simplified to compute the descriptor
      directly, no lambda.
- [ ] dsl module tests green; `make lint` passes.

## Tier

`backend`

## Files to create/modify

- Modify: `backend/dsl-platform/dsl/src/main/java/cbs/nova/dsl/function/FunctionBuilder.java`
- Modify: sibling builders found via codegraph
- Modify: tests listed above + any example DSLs

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl:test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- API-breaking change to DSL builder surface — keep migration mechanical, no behavior change.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
