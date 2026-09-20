# T624 — DslSignalService unit spec

## Goal

Add unit spec for `DslSignalService` (82 LOC): `sendSignal` and `querySignalState`, mocking the
Temporal workflow stub.

## Why

Signals feature (T567) is operator-facing — `POST` signal to a running process, query signal
state. Zero direct test references today. Wrong runId/signal-name handling or state-query mapping
regressions would only surface against a live Temporal.

## Acceptance criteria

- [ ] `sendSignal` happy path: correct stub invocation, runId/signal name propagation, result
      mapping (`SignalResult` fields).
- [ ] `sendSignal` failure paths: unknown run / stub throws — pinned per current behavior.
- [ ] `querySignalState`: state present → map returned; absent/null → null per contract.
- [ ] Mock style consistent with sibling tests that fake Temporal stubs (search
      `codegraph_search WorkflowStub` in tests for precedent).
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/service/DslSignalServiceTest.java`
- Read first: `DslSignalService.java`, `DslSignalsHandler` + `DslEventHandlerTest` for stub-mock precedent.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests 'cbs.nova.starter.service.DslSignalServiceTest'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no service changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
