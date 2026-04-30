# CBS-Nova — Implementation Plan

> Single source of truth for the CBS-Nova orchestration engine build-out.
>
> **Workflow:** For each task, create a spec under `docs/tasks/{id}-{slug}.md`
> from [task-template.md](task-template.md), delegate (see `/executor-delegation`),
> save result to `docs/results/{id}-{slug}.result.md` from [result-template.md](result-template.md),
> then mark task `done` here.

> **v0.7 Pivot Note:** Kotlin Script (.kts) DSL is abandoned. All DSL work now targets a Java DSL
> with dual execution modes: `GENERATED` (compile-time code generation of Temporal workflows/activities)
> and `REFLECTED` (reflection-based runtime for dev). Tasks referencing `.kts` or Kotlin DSL are
> historical and may need re-specification for the Java DSL approach.

## Status Legend

| Symbol        | Meaning                                           |
|---------------|---------------------------------------------------|
| `todo`        | Not started                                       |
| `in-progress` | Actively being worked on                          |
| `done`        | Completed and verified                            |
| `blocked`     | Waiting on another task                           |
| `parked`      | Locked task, excluded from planning and execution |

---

## Active Task Summary

| ID  | Title                                                            | Status      | Module     |
|-----|------------------------------------------------------------------|-------------|------------|
| T60 | Delete from `dsl-api` Kotlin files. Create new Java api          | `todo`      | dsl-api    |
| T61 | Delete module `dsl`, create new for java                         | `todo`      | dsl        |
| T62 | Rewrite `dsl` runtime in Java                                    | `todo`      | dsl        |
| T63 | Rewrite `dsl` impl layer in Java                                 | `todo`      | dsl        |
| T64 | Extend `dsl-codegen` processor for Event + Workflow              | `todo`      | dsl-codegen|
| T65 | Generate Temporal Workflow wrappers                              | `todo`      | dsl-codegen|
| T66 | Generate Temporal Activity wrappers                              | `todo`      | dsl-codegen|
| T67 | Rewrite `TemporalTransactionsScope` from Kotlin to Java          | `todo`      | starter    |
| T68 | Rewrite `DslLoader` for Java DSL                                 | `todo`      | backend    |
| T69 | Rewrite `DevDslController` for Java DSL                          | `todo`      | backend    |
| T70 | Update `EventWorkflowImpl` for Java DSL registry                 | `todo`      | starter    |
| T71 | Update `TransactionActivityImpl` for Java DSL registry           | `todo`      | starter    |
| T72 | Prototype E2E test                                               | `todo`      | starter    |
| T73 | `ReflectiveEventExecutor` for dev mode                           | `todo`      | dsl        |
| T74 | Remove Kotlin dependencies from `dsl/build.gradle`               | `todo`      | dsl        |
| T75 | Update module build files                                        | `todo`      | build      |
| T76 | Create sample `.java` DSL files in `examples/cbs-rules/`         | `todo`      | examples   |
| T77 | Update `DslConfig`                                               | `todo`      | backend    |
| T78 | Java DSL builder fluent API design                               | `todo`      | dsl-api    |
| T79 | Generate input mappers                                           | `todo`      | dsl-codegen|
| T80 | Unified Java DSL builder with all closures                       | `todo`      | dsl        |

---

## Phase Summary

| Phase    | Status      | Tasks            | Documentation                                                             |
|----------|-------------|------------------|---------------------------------------------------------------------------|
| 1      | `done`      | T01–T24          | [phase1.md](papers/phase1.md)                                             |
| 2      | `done`      | T25–T40          | [phase2.md](papers/phase2.md)                                             |
| 3      | `done`      | T41–T59          | [phase3.md](papers/phase3.md)    |
| 4      | `done`      | T60–T80          | Java DSL pivot, prototype, codegen    |
