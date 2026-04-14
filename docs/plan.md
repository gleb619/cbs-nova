# CBS-Nova Implementation Plan

**Version:** 1.0 | **Date:** 2026-04-12 | **Status:** Active

---

## How This Works

Each session: pick a `TODO` task whose `BLOCKED BY` are all `DONE`, create a task file at `docs/tasks/<id>-<slug>.md`
from [task-template.md](task-template.md), delegate to Qwen (see `/qwen-delegation`), save result to
`docs/results/<id>-<slug>.result.md` from [result-template.md](result-template.md), then mark task `DONE` here.

**Statuses:** `TODO` · `IN_PROGRESS` · `BLOCKED` · `REVIEW` · `DONE`

---

## Module Map

| Module             | Type            | Purpose                                                                                    |
|--------------------|-----------------|--------------------------------------------------------------------------------------------|
| `backend`          | Spring Boot app | Entry point, security, OpenAPI, Temporal worker/client config                              |
| `starter`          | Library JAR     | All domain features: entities, repos, services, controllers, Flyway                        |
| `dsl/dsl-api`      | Library JAR     | Shared DSL interfaces/contracts for both compiled runtime and lenient dev execution        |
| `dsl/dsl-compiler` | Library JAR     | DSL compile/validate/publish pipeline (`.kts` → Java classes/JAR, Maven local publication) |
| `dsl/dsl-runtime`  | Library JAR     | Runtime DSL loading + lenient development interpreter mode                                 |
| `client`           | Generated JAR   | Feign + TypeScript clients from OpenAPI                                                    |
| `frontend`         | Nuxt 3 SPA      | Admin UI (hexagonal, backend-first — Phase 7)                                              |
| `frontend-plugin`  | Nuxt layer      | Shared domain types, ports, Vue components                                                 |

**Dependency graph:** `backend → starter → (none)` · `backend → dsl/dsl-runtime → dsl/dsl-api` ·
`backend → dsl/dsl-compiler → dsl/dsl-api` · `client → backend`

---

## Task Table

| ID  | Title                                                                                                       | Phase    | Status | Blocked By    | Blocks             |
|-----|-------------------------------------------------------------------------------------------------------------|----------|--------|---------------|--------------------|
| T01 | Temporal + Temporal UI in Docker Compose                                                                    | 0-Infra  | DONE   | —             | T09                |
| T02 | Gitea in Docker Compose + examples/cbs-rules                                                                | 0-Infra  | DONE   | —             | T06                |
| T03 | Create `dsl` Gradle module (Kotlin)                                                                         | 0-Infra  | DONE   | —             | T04                |
| T04 | DSL API: Kotlin interfaces & context types                                                                  | 1-DSL    | DONE   | T03           | T05                |
| T05 | DSL Runtime: builders, registry, stub workflow                                                              | 1-DSL    | DONE   | T04           | T06, T10, T14, T18 |
| T06 | DSL Compiler: Gradle tasks + semantic validator                                                             | 1-DSL    | DONE   | T02, T05      | T20                |
| T07 | Flyway: workflow_execution, event_execution, transition_log                                                 | 2-DB     | DONE   | —             | T12                |
| T08 | Flyway: mass_operation_execution, mass_operation_item                                                       | 2-DB     | DONE   | —             | T17                |
| T09 | Temporal client + worker config in backend                                                                  | 3-Engine | DONE   | T01           | T10, T14           |
| T10 | EventWorkflow + TransactionActivity (Temporal)                                                              | 3-Engine | DONE   | T05, T07, T09 | T11                |
| T11 | Core services: EventService, WorkflowResolver, WorkflowExecutor, ContextEvaluator, ContextEncryptionService | 3-Engine | DONE   | T10, T12      | T13                |
| T12 | State repos + entities: WorkflowExecution, EventExecution, TransitionLog                                    | 3-Engine | DONE   | T07           | T11                |
| T13 | EventController: POST /api/events/execute + DslLoader                                                       | 3-Engine | DONE   | T11           | T21                |
| T14 | MassOpWorkflow + MassOpItemActivity (Temporal)                                                              | 4-MassOp | DONE   | T05, T08, T09 | T15                |
| T15 | MassOp services: MassOperationService, MassOperationScheduler, SignalEmitter                                | 4-MassOp | DONE   | T14, T17      | T16                |
| T16 | MassOperationController: trigger, status, items, retry                                                      | 4-MassOp | DONE   | T15           | T23                |
| T17 | MassOp repos + entities: MassOperationExecution, MassOperationItem                                          | 4-MassOp | DONE   | T08           | T15                |
| T18 | BPMN: StaticBpmnGenerator + BpmnExporter                                                                    | 5-BPMN   | DONE   | T05           | T19                |
| T19 | BpmnController: GET /api/workflows/{code}/bpmn                                                              | 5-BPMN   | DONE   | T18           | T22                |
| T20 | DevDslController: POST /dev/dsl/execute @Profile("dev")                                                     | 6-Dev    | DONE   | T05, T06      | —                  |
| T21 | Frontend: execution list + detail page                                                                      | 7-FE     | DONE   | T13           | T22, T24           |
| T22 | Frontend: BPMN viewer (bpmn-js integration)                                                                 | 7-FE     | DONE   | T19, T21      | —                  |
| T23 | Frontend: MassOperation report UI                                                                           | 7-FE     | DONE   | T16           | —                  |
| T24 | Frontend: Navigation ABAC (sidebar roles, route guard)                                                      | 7-FE     | DONE   | T21           | —                  |
| T25 | DSL module split: rename `dsl` to `dsl/dsl-api` and remove deprecated classes                               | 8-DSL-R2 | TODO   | —             | T26, T27           |
| T26 | New `dsl/dsl-compiler` submodule: compile `.kts` to Java classes/JAR for backend                            | 8-DSL-R2 | TODO   | T25           | T28, T29           |
| T27 | New `dsl/dsl-runtime` submodule: runtime loader + lenient dev interpreter                                   | 8-DSL-R2 | TODO   | T25           | T30                |
| T28 | `dsl/dsl-compiler` Gradle task: prepare DSL JAR and publish to `mavenLocal`                                 | 8-DSL-R2 | TODO   | T26           | T29                |
| T29 | `backend` integration: JGit download/update DSL, invoke compiler flow, consume artifact as `runtimeOnly`    | 8-DSL-R2 | TODO   | T26, T28      | T30                |
| T30 | Lenient mode refactor: replace `DevDslController` with JGit sync + non-compiled execution path              | 8-DSL-R2 | TODO   | T27, T29      | —                  |

---

## Phase Summary

| Phase    | Status | Tasks   | Key Accomplishments                                 | Documentation                                                     |
|----------|--------|---------|-----------------------------------------------------|-------------------------------------------------------------------|
| 0-Infra  | DONE   | T01-T03 | Temporal, Gitea, Docker setup, DSL module           | [phase1.md](papers/phase1.md#phase-0---infrastructure-setup)      |
| 1-DSL    | DONE   | T04-T06 | Kotlin DSL API, Runtime builders, Compiler pipeline | [phase1.md](papers/phase1.md#phase-1---dsl-foundation)            |
| 2-DB     | DONE   | T07-T08 | Core workflow tables, Mass operation tables         | [phase1.md](papers/phase1.md#phase-2---database-schema)           |
| 3-Engine | DONE   | T09-T13 | Temporal integration, Core services, Event API      | [phase1.md](papers/phase1.md#phase-3---core-orchestration-engine) |
| 4-MassOp | DONE   | T14-T17 | Mass operation workflows, Services, Controllers     | [phase1.md](papers/phase1.md#phase-4---mass-operations)           |
| 5-BPMN   | DONE   | T18-T19 | BPMN export, Static generation, API endpoint        | [phase1.md](papers/phase1.md#phase-5---bpmn-export)               |
| 6-Dev    | DONE   | T20     | Development DSL mode, Inline evaluation             | [phase1.md](papers/phase1.md#phase-6---dev-dsl-mode)              |
| 7-FE     | DONE   | T21-T24 | Vue 3/Nuxt 3 frontend, Execution UI, BPMN viewer    | [phase1.md](papers/phase1.md#phase-7---frontend)                  |
| 8-DSL-R2 | TODO   | T25-T30 | DSL module split, Compiler/Runtime refactor         | Current phase                                                     |

**Total Completed:** 24 tasks across 7 phases | **Current:** Phase 8 (DSL Refactor)

---

## Task Template

Each task should follow this structure:

```markdown
#### T## · Task Title

**Status:** TODO/DONE
**Goal:** Brief description of objective
**Files to touch:** List of files with relative paths
**Implementation details:** Key technical requirements
**Acceptance criteria:** How to verify completion
**Dependencies:** What must be done first
```


---

## Dependency Graph (Visual)

```
T01 ──────────────────────────────────► T09
T02 ──────────────────────────────────► T06
T03 ──► T04 ──► T05 ──────────────────► T06
                 │                       ▼
                 ├──────────────────────► T10 ──► T11 ──► T13 ──► T21 ──► T22
                 │                                          │              T24
                 ├──────────────────────► T14 ──► T15 ──► T16 ──► T23
                 └──────────────────────► T18 ──► T19 ──► T22
T07 ──────────────────────────────────► T12 ──────────────► T11
T08 ──────────────────────────────────► T17 ──────────────► T15
T05 + T06 ────────────────────────────► T20
T09 ──────────────────────────────────► T10, T14
T25 ──────────────────────────────────► T26 ──► T28 ──► T29 ──► T30
  └───────────────────────────────────► T27 ────────────────► T30
```

---

## Next Recommended Session Start Order

Parallel-safe starting tasks (no blockers): **T25** first, then **T26 + T27** in parallel.
