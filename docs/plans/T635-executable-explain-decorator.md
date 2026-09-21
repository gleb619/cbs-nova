# T635 — Remove Executable.explain() default impl; move to pipeline decorator

## Goal

Delete the `@Deprecated` default `Executable.explain(Context)` implementation (and its private
`firstLine`/`derivedDescription` helpers) at `Executable.java:44-60`. Move that
descriptor/ description-derived default report logic into a decorator/stage on the execution
pipeline (per the TODO: "remove impl, instead add a some decorator in PipeStage").

## Why

`Executable.java:46` TODO. The interface default builds an `ExplainReport` from
`describe()`/`description()` — behavior living in the API interface instead of the execution
pipeline. Sole production caller: `DefaultHelperRunner.runHelper` EXPLAIN branch
(`DefaultHelperRunner.java:36`).

## Acceptance criteria

- [ ] Default `explain()` + private helpers removed from `Executable`; interface keeps only the
      contract (or drops the method entirely if the decorator fully replaces it — pick one,
      document in plan execution).
- [ ] Default explain behavior (name from descriptor, first-line description, markdown passthrough,
      `withInfo(name, …)` enrichment) reproduced by the decorator/stage; existing explain outputs
      byte-identical (check existing explain specs as characterization).
- [ ] `DefaultHelperRunner` EXPLAIN branch routes through the decorator.
- [ ] dsl + dsl-api + starter explain-related tests green.
- [ ] `make lint` passes.

## Tier

`backend`

## Files to create/modify

- Modify: `backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/Executable.java`
- Modify: `backend/dsl-platform/dsl/src/main/java/cbs/nova/dsl/runner/DefaultHelperRunner.java`
- Create: decorator/stage class (location per pipeline package convention — see
  `core/stage/` siblings and `DslExecutionPipeline`).
- Read first: `ExplainSupport.java`, explain specs (`codegraph_callers` on `explain(`).

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform test
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Explain outputs must not change — pure relocation of default logic.
- Explain refactoring session active on main — rebase-check before starting.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
