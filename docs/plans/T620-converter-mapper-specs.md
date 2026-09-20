# T620 — Converter mapper specs (Introspection/Run/TransactionExecution/Audit)

## Goal

Add unit specs for the four untested converters in `cbs.nova.starter.converter`:
`DslIntrospectionMapper` (169 LOC), `TransactionExecutionMapper`, `DslRunMapper`, `DslAuditMapper`.

## Why

Converter package is 5/10 tested. These four map entities to API-facing DTOs (introspection
surface, run/execution detail responses, audit trail). Mapping regressions silently change API
responses. Pure unit scope — no Spring context needed.

## Acceptance criteria

- [ ] `DslIntrospectionMapperTest` — full field mapping incl. nulls/empty collections; this is the
      largest surface, prioritize it.
- [ ] `TransactionExecutionMapperTest` — status mapping, timestamps, null-safe paths.
- [ ] `DslRunMapperTest` — entity→DTO field fidelity.
- [ ] `DslAuditMapperTest` — field fidelity.
- [ ] Characterization style: pin current behavior (including quirks), don't change mapper logic.
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/converter/DslIntrospectionMapperTest.java`
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/converter/TransactionExecutionMapperTest.java`
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/converter/DslRunMapperTest.java`
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/converter/DslAuditMapperTest.java`
- Follow sibling style: `DslRuntimeMapperTest`, `MapInputConverterTest`.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests 'cbs.nova.starter.converter.*'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no mapper changes in this task.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
