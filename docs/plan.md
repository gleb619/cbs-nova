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

| ID   | Title                                                        | Status | Module   | Blocked By    | Blocks             |
|------|--------------------------------------------------------------|--------|----------|---------------|--------------------|
| T01  | Temporal + Temporal UI in Docker Compose                     | `done` | infra    | —             | T09                |
| T02  | Gitea in Docker Compose + examples/cbs-rules                 | `done` | infra    | —             | T06                |
| T03  | Create `dsl` Gradle module (Kotlin)                          | `done` | dsl      | —             | T04                |
| T04  | DSL API: Kotlin interfaces & context types                   | `done` | dsl      | T03           | T05                |
| T05  | DSL Runtime: builders, registry, stub workflow               | `done` | dsl      | T04           | T06, T10, T14, T18 |
| T06  | DSL Compiler: Gradle tasks + semantic validator              | `done` | dsl      | T02, T05      | T20                |
| T07  | Flyway: workflow_execution, event_execution, transition_log  | `done` | starter  | —             | T12                |
| T08  | Flyway: mass_operation_execution, mass_operation_item        | `done` | starter  | —             | T17                |
| T09  | Temporal client + worker config in backend                   | `done` | backend  | T01           | T10, T14           |
| T10  | EventWorkflow + TransactionActivity (Temporal)               | `done` | backend  | T05, T07, T09 | T11                |
| T11  | Core services: EventService, WorkflowResolver, etc.          | `done` | starter  | T10, T12      | T13                |
| T12  | State repos + entities: WorkflowExecution, EventExecution    | `done` | starter  | T07           | T11                |
| T13  | EventController: POST /api/events/execute + DslLoader        | `done` | starter  | T11           | T21                |
| T14  | MassOpWorkflow + MassOpItemActivity (Temporal)               | `done` | backend  | T05, T08, T09 | T15                |
| T15  | MassOp services: MassOperationService, Scheduler, Emitter    | `done` | starter  | T14, T17      | T16                |
| T16  | MassOperationController: trigger, status, items, retry       | `done` | starter  | T15           | T23                |
| T17  | MassOp repos + entities                                      | `done` | starter  | T08           | T15                |
| T18  | BPMN: StaticBpmnGenerator + BpmnExporter                     | `done` | starter  | T05           | T19                |
| T19  | BpmnController: GET /api/workflows/{code}/bpmn               | `done` | starter  | T18           | T22                |
| T20  | DevDslController: POST /dev/dsl/execute @Profile("dev")      | `done` | backend  | T05, T06      | —                  |
| T21  | Frontend: execution list + detail page                       | `done` | frontend | T13           | T22, T24           |
| T22  | Frontend: BPMN viewer (bpmn-js integration)                  | `done` | frontend | T19, T21      | —                  |
| T23  | Frontend: MassOperation report UI                            | `done` | frontend | T16           | —                  |
| T24  | Frontend: Navigation ABAC (sidebar roles, route guard)       | `done` | frontend | T21           | —                  |
| T25  | Create `dsl-api` Gradle module (shared contract)             | `done` | dsl-api  | —             | T26a               |
| T26a | `@KotlinScript` host (replace JSR-223)                       | `done` | dsl      | T25           | T26b               |
| T26b | `EventDslScope` + `TransactionsScope`                        | `done` | dsl      | T26a          | T26c               |
| T26c | `WorkflowBuilder` closure-based transitions                  | `todo` | dsl      | T26b          | T26d               |
| T26d | E2E test (Testcontainers + Temporal)                         | `todo` | dsl      | T26c, T01     | T27                |
| T27  | Backend integration: wire new DSL, remove deprecated classes | `todo` | backend  | T26d          | —                  |

---

## Phase Summary

| Phase    | Status | Tasks   | Documentation                                                     |
|----------|--------|---------|-------------------------------------------------------------------|
| 0-Infra  | `done` | T01–T03 | [phase1.md](papers/phase1.md#phase-0---infrastructure-setup)      |
| 1-DSL    | `done` | T04–T06 | [phase1.md](papers/phase1.md#phase-1---dsl-foundation)            |
| 2-DB     | `done` | T07–T08 | [phase1.md](papers/phase1.md#phase-2---database-schema)           |
| 3-Engine | `done` | T09–T13 | [phase1.md](papers/phase1.md#phase-3---core-orchestration-engine) |
| 4-MassOp | `done` | T14–T17 | [phase1.md](papers/phase1.md#phase-4---mass-operations)           |
| 5-BPMN   | `done` | T18–T19 | [phase1.md](papers/phase1.md#phase-5---bpmn-export)               |
| 6-Dev    | `done` | T20     | [phase1.md](papers/phase1.md#phase-6---dev-dsl-mode)              |
| 7-FE     | `done` | T21–T24 | [phase1.md](papers/phase1.md#phase-7---frontend)                  |
| 8-DSL-R2 | `todo` | T25–T27 | Current phase — task specs in `docs/tasks/`                       |
