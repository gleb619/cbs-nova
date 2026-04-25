# CBS-Nova — Implementation Plan

> Single source of truth for the CBS-Nova orchestration engine build-out.
>
> **Workflow:** For each task, create a spec under `docs/tasks/{id}-{slug}.md`
> from [task-template.md](task-template.md), delegate (see `/executor-delegation`),
> save result to `docs/results/{id}-{slug}.result.md` from [result-template.md](result-template.md),
> then mark task `done` here.

## Status Legend

| Symbol        | Meaning                                           |
|---------------|---------------------------------------------------|
| `todo`        | Not started                                       |
| `in-progress` | Actively being worked on                          |
| `done`        | Completed and verified                            |
| `blocked`     | Waiting on another task                           |
| `parked`      | Locked task, excluded from planning and execution |

---

## Task Table

| ID  | Title                                                                                                      | Status   | Module       | Blocked By     | Blocks   |
|-----|------------------------------------------------------------------------------------------------------------|----------|--------------|----------------|----------|
| T41 | E2E tests in `starter` with `TestWorkflowEnvironment` + real `.kts` resources                              | `parked` | starter      | T38d, T39, T40 | —        |
| T42 | Helper DSL — add `parameters` and `context` closures                                                       | `done`   | dsl          | T41            | T46, T47 |
| T43 | Transaction DSL — add `parameters` and `context` closures                                                  | `done`   | dsl          | T41            | T46      |
| T44 | Condition DSL — add `parameters` and `context` closures                                                    | `done`   | dsl          | T41            | T46      |
| T45 | MassOperation DSL — add `parameters` closure                                                               | `done`   | dsl          | T41            | T46      |
| T46 | Code-based helper import via `#import` preprocessor                                                        | `done`   | dsl          | T42, T43, T44  | T47, T48 |
| T47 | Create `dsl-codegen` gradle module with annotation processor for compile-time `@DslComponent` registration | `done`   | dsl-api, dsl | -              | -        |
| T48 | Redesign `TestHelper` as real executable implementation with optional `execute` override                   | `todo`   | dsl          | -              | -        |
| T49 | Replace `CodeImportResolver` runtime classpath scanning with compile-time generated SPI                    | `done`   | dsl          | -              | -        |
| T50 | Integrate Avaje Jsonb and document JSON-native DSL parameters                                              | `done`   | dsl-api      | T49            | T51-T58  |
| T51 | Consolidate `dsl-api` types by domain to reduce file count                                                 | `done`   | dsl-api      | T49            | T52-T58  |
| T52 | Unify `HelperDefinition` — merge `HelperFunction`, add parameter-aware `HelperInput`                       | `done`   | dsl-api, dsl | T51            | T58      |
| T53 | Add `TransactionInput`/`TransactionOutput` and unify `TransactionDefinition`                               | `done`   | dsl-api, dsl | T51            | T58      |
| T54 | Add `EventInput`/`EventOutput` and unify `EventDefinition`                                                 | `done`   | dsl-api, dsl | T51            | T58      |
| T55 | Add `ConditionInput`/`ConditionOutput` and unify `ConditionDefinition`                                     | `done`   | dsl-api, dsl | T51            | T58      |
| T56 | Add `WorkflowInput`/`WorkflowOutput` and unify `WorkflowDefinition`                                        | `done`   | dsl-api, dsl | T51            | T58      |
| T57 | Add `MassOperationInput`/`MassOperationOutput` and unify `MassOperationDefinition`                         | `done`   | dsl-api, dsl | T51            | T58      |
| T58 | Extend `dsl-codegen` processor to extract required/optional parameter metadata from Input types            | `todo`   | dsl-codegen  | T52-T57        | -        |

---

## Phase Summary

| Phase    | Status   | Tasks   | Documentation                                                             |
|----------|----------|---------|---------------------------------------------------------------------------|
| 0–7      | `done`   | T01–T24 | [phase1.md](papers/phase1.md)                                             |
| 8–10     | `done`   | T25–T40 | [phase2.md](papers/phase2.md)                                             |
