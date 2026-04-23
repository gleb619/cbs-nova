# CBS-Nova — Phase 2: DSL Round 2 & 3, E2E Foundation

> **Scope:** T25–T40 (Phases 8–10)
> Covers DSL rewrites with `@KotlinScript`, comprehensive scopes for all DSL types, in-memory execution, and Temporal workflow migration to `starter`.

---

## Status Legend

| Symbol        | Meaning                                           |
|---------------|---------------------------------------------------|
| `todo`        | Not started                                       |
| `in-progress` | Actively being worked on                          |
| `done`        | Completed and verified                            |
| `blocked`     | Waiting on another task                           |
| `parked`      | Locked task, excluded from planning and execution |

---

## Task Table (Phase 2)

| ID   | Title                                                                                               | Status   | Module   | Blocked By              | Blocks             |
|------|-----------------------------------------------------------------------------------------------------|----------|----------|-------------------------|--------------------|
| T25  | Create `dsl-api` Gradle module (shared contract)                                                    | `done`   | dsl-api  | —                       | T26a               |
| T26a | `@KotlinScript` host (replace JSR-223)                                                              | `done`   | dsl      | T25                     | T26b               |
| T26b | `EventDslScope` + `TransactionsScope`                                                               | `done`   | dsl      | T26a                    | T26c               |
| T26c | `WorkflowBuilder` closure-based transitions                                                         | `done`   | dsl      | T26b                    | T26d               |
| T26d | E2E test (Testcontainers + Temporal)                                                                | `parked` | dsl      | T26c, T01               | T27                |
| T27  | Backend integration: wire new DSL, remove deprecated classes                                        | `parked` | backend  | T26d                    | —                  |
| T28  | `WorkflowDslScope`: states, initial, terminal, transitions                                          | `done`   | dsl      | T26d                    | T29                |
| T29  | `EventDslScope`: parameters, context, display, finish blocks                                        | `done`   | dsl      | T28                     | T30                |
| T30  | `TransactionDslScope`: preview, execute, rollback, delegate                                         | `done`   | dsl      | T29                     | T31                |
| T31  | `HelperDslScope`: inline helper declarations, chaining                                              | `done`   | dsl      | T26d                    | T32                |
| T32  | `ConditionDslScope`: named condition declarations                                                   | `done`   | dsl      | T30, T31                | T33                |
| T33  | `MassOperationDslScope`: triggers, context, source, lock, item, signals, finish                     | `done`   | dsl      | T26d                    | T34                |
| T34  | DSL import system: `#import` resolver + scope injection                                             | `done`   | dsl      | T28,T29,T30,T31,T32,T33 | T35                |
| T35  | DSL integration test: full loan-contract scenario (all types)                                       | `done`   | dsl      | T34                     | T36                |
| T36  | DSL sample `.kts` resource files + parameterized compiler tests                                     | `done`   | dsl      | T35                     | T36b               |
| T36b | DSL execution IT test: concrete transaction + helper + in-process runner                            | `done`   | dsl      | T36                     | T37                |
| T37  | Backend integration: wire all new DSL scopes, remove deprecated                                     | `done`   | backend  | T36b                    | —                  |
| T38  | Standard event `.kts` file + complete loan-disbursement scenario in `dsl`                           | `done`   | dsl      | T37                     | T38b               |
| T38b | Add `name` field to DSL builders + `TestHelper`/`TestTransaction`/`TestCondition`/`TestEvent` impls | `done`   | dsl      | T38                     | T38c               |
| T38c | `ImplRegistry` — runtime dispatch from DSL code/name to `TestXxx` impl classes + `.kts` wiring      | `done`   | dsl      | T38b                    | T38d, T39          |
| T38d | Annotation processor + compile-time `ImplRegistry` population — `@DslImpl` annotation + design doc  | `done`   | dsl-api  | T38c                    | T41                |
| T39  | In-memory transaction/helper execution impl (no Temporal) in `dsl`                                  | `done`   | dsl      | T38c                    | T41                |
| T40  | Move `EventWorkflowImpl` + Temporal wiring to `starter`                                             | `done`   | starter  | T37                     | T41                |

---

## Phase Summary

| Phase    | Status | Tasks   | Notes                                                |
|----------|--------|---------|------------------------------------------------------|
| 8-DSL-R2 | `done` | T25–T27 | `@KotlinScript` host replacing JSR-223               |
| 9-DSL-R3 | `done` | T28–T37 | DSL scopes per type + full integration               |
| 10-E2E   | `done` | T38–T40 | Standard kts + in-memory runner + Temporal migration |

---

## Artifacts

Task specifications: `docs/papers/phase2/`
