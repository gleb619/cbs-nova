# CBS-Nova Implementation Plan

**Version:** 1.1 | **Date:** 2026-04-18 | **Status:** Active

---

## How This Works

Each session: pick a `TODO` task whose `BLOCKED BY` are all `DONE`, create a task file at `docs/tasks/<id>-<slug>.md`
from [task-template.md](task-template.md), delegate (see `/executor-delegation`), save result to
`docs/results/<id>-<slug>.result.md` from [result-template.md](result-template.md), then mark task `DONE` here.

**Statuses:** `TODO` · `IN_PROGRESS` · `BLOCKED` · `REVIEW` · `DONE`

---

## Module Map

| Module            | Type            | Purpose                                                                       |
|-------------------|-----------------|-------------------------------------------------------------------------------|
| `backend`         | Spring Boot app | Entry point, security, OpenAPI, Temporal worker/client config                 |
| `starter`         | Library JAR     | All domain features: entities, repos, services, controllers, Flyway           |
| `dsl-api`         | Library JAR     | Java interfaces, records, POJOs — shared contract between `dsl` and `backend` |
| `dsl`             | Library JAR     | Kotlin DSL logic: builders, registry, stub workflow, lenient dev execution    |
| `client`          | Generated JAR   | Feign + TypeScript clients from OpenAPI                                       |
| `frontend`        | Nuxt 3 SPA      | Admin UI (hexagonal, backend-first — Phase 7)                                 |
| `frontend-plugin` | Nuxt layer      | Shared domain types, ports, Vue components                                    |

**Dependency graph:** `backend → starter` · `backend → dsl → dsl-api` · `backend → dsl-api` · `client → backend`

---

## Task Table

| ID   | Title                                                                                                                                                                                                                         | Phase    | Status | Blocked By    | Blocks             |
|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|--------|---------------|--------------------|
| T01  | Temporal + Temporal UI in Docker Compose                                                                                                                                                                                      | 0-Infra  | DONE   | —             | T09                |
| T02  | Gitea in Docker Compose + examples/cbs-rules                                                                                                                                                                                  | 0-Infra  | DONE   | —             | T06                |
| T03  | Create `dsl` Gradle module (Kotlin)                                                                                                                                                                                           | 0-Infra  | DONE   | —             | T04                |
| T04  | DSL API: Kotlin interfaces & context types                                                                                                                                                                                    | 1-DSL    | DONE   | T03           | T05                |
| T05  | DSL Runtime: builders, registry, stub workflow                                                                                                                                                                                | 1-DSL    | DONE   | T04           | T06, T10, T14, T18 |
| T06  | DSL Compiler: Gradle tasks + semantic validator                                                                                                                                                                               | 1-DSL    | DONE   | T02, T05      | T20                |
| T07  | Flyway: workflow_execution, event_execution, transition_log                                                                                                                                                                   | 2-DB     | DONE   | —             | T12                |
| T08  | Flyway: mass_operation_execution, mass_operation_item                                                                                                                                                                         | 2-DB     | DONE   | —             | T17                |
| T09  | Temporal client + worker config in backend                                                                                                                                                                                    | 3-Engine | DONE   | T01           | T10, T14           |
| T10  | EventWorkflow + TransactionActivity (Temporal)                                                                                                                                                                                | 3-Engine | DONE   | T05, T07, T09 | T11                |
| T11  | Core services: EventService, WorkflowResolver, WorkflowExecutor, ContextEvaluator, ContextEncryptionService                                                                                                                   | 3-Engine | DONE   | T10, T12      | T13                |
| T12  | State repos + entities: WorkflowExecution, EventExecution, TransitionLog                                                                                                                                                      | 3-Engine | DONE   | T07           | T11                |
| T13  | EventController: POST /api/events/execute + DslLoader                                                                                                                                                                         | 3-Engine | DONE   | T11           | T21                |
| T14  | MassOpWorkflow + MassOpItemActivity (Temporal)                                                                                                                                                                                | 4-MassOp | DONE   | T05, T08, T09 | T15                |
| T15  | MassOp services: MassOperationService, MassOperationScheduler, SignalEmitter                                                                                                                                                  | 4-MassOp | DONE   | T14, T17      | T16                |
| T16  | MassOperationController: trigger, status, items, retry                                                                                                                                                                        | 4-MassOp | DONE   | T15           | T23                |
| T17  | MassOp repos + entities: MassOperationExecution, MassOperationItem                                                                                                                                                            | 4-MassOp | DONE   | T08           | T15                |
| T18  | BPMN: StaticBpmnGenerator + BpmnExporter                                                                                                                                                                                      | 5-BPMN   | DONE   | T05           | T19                |
| T19  | BpmnController: GET /api/workflows/{code}/bpmn                                                                                                                                                                                | 5-BPMN   | DONE   | T18           | T22                |
| T20  | DevDslController: POST /dev/dsl/execute @Profile("dev")                                                                                                                                                                       | 6-Dev    | DONE   | T05, T06      | —                  |
| T21  | Frontend: execution list + detail page                                                                                                                                                                                        | 7-FE     | DONE   | T13           | T22, T24           |
| T22  | Frontend: BPMN viewer (bpmn-js integration)                                                                                                                                                                                   | 7-FE     | DONE   | T19, T21      | —                  |
| T23  | Frontend: MassOperation report UI                                                                                                                                                                                             | 7-FE     | DONE   | T16           | —                  |
| T24  | Frontend: Navigation ABAC (sidebar roles, route guard)                                                                                                                                                                        | 7-FE     | DONE   | T21           | —                  |
| T25  | Create `dsl-api` Gradle module: Java interfaces + records shared across the project (Action, Signal, ExecutionResult, HelperFunction, TransactionDefinition, WorkflowDefinition, EventDefinition, context types)              | 8-DSL-R2 | TODO   | —             | T26a               |
| T26a | `@KotlinScript` definition + `BasicJvmScriptingHost`: replace JSR-223 `DslScriptHost` with proper `EventScriptCompilationConfiguration` / `EventScriptEvaluationConfiguration`, `defaultImports`, `constructorArgs` injection | 8-DSL-R2 | TODO   | T25           | T26b               |
| T26b | `EventDslScope` + `TransactionsScope`: top-level `event{}` function on implicit receiver, `transactions { ctx -> }` as suspend lambda, `ctx.step()` / `ctx.await()` / `ctx.step { when/then/otherwise }`                      | 8-DSL-R2 | TODO   | T26a          | T26c               |
| T26c | `WorkflowBuilder` closure-based transitions: `transitions { ctx -> FROM -> TO on Action.X { ... } onFault { ... } }`, infer states/initial/terminal when omitted                                                              | 8-DSL-R2 | TODO   | T26b          | T26d               |
| T26d | E2E test (Testcontainers + Temporal): create stub transactions/helpers in `dsl` test sources, write `loan-disbursement.event.kts`, compile via `BasicJvmScriptingHost`, run full chain, assert execution trace                | 8-DSL-R2 | TODO   | T26c, T01     | T27                |
| T27  | `backend` integration: wire `dsl` → `dsl-api` dependencies, remove deprecated `DslRegistry` / `GiteaRulesSource` / `DslScriptHost` / `StubWorkflowActivity` / `DslCompiler`, update `DevDslController` to use new host        | 8-DSL-R2 | TODO   | T26d          | —                  |

---

## Phase Summary

| Phase    | Status | Tasks   | Key Accomplishments                                                                             | Documentation                                                     |
|----------|--------|---------|-------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| 0-Infra  | DONE   | T01-T03 | Temporal, Gitea, Docker setup, DSL module                                                       | [phase1.md](papers/phase1.md#phase-0---infrastructure-setup)      |
| 1-DSL    | DONE   | T04-T06 | Kotlin DSL API, Runtime builders, Compiler pipeline                                             | [phase1.md](papers/phase1.md#phase-1---dsl-foundation)            |
| 2-DB     | DONE   | T07-T08 | Core workflow tables, Mass operation tables                                                     | [phase1.md](papers/phase1.md#phase-2---database-schema)           |
| 3-Engine | DONE   | T09-T13 | Temporal integration, Core services, Event API                                                  | [phase1.md](papers/phase1.md#phase-3---core-orchestration-engine) |
| 4-MassOp | DONE   | T14-T17 | Mass operation workflows, Services, Controllers                                                 | [phase1.md](papers/phase1.md#phase-4---mass-operations)           |
| 5-BPMN   | DONE   | T18-T19 | BPMN export, Static generation, API endpoint                                                    | [phase1.md](papers/phase1.md#phase-5---bpmn-export)               |
| 6-Dev    | DONE   | T20     | Development DSL mode, Inline evaluation                                                         | [phase1.md](papers/phase1.md#phase-6---dev-dsl-mode)              |
| 7-FE     | DONE   | T21-T24 | Vue 3/Nuxt 3 frontend, Execution UI, BPMN viewer                                                | [phase1.md](papers/phase1.md#phase-7---frontend)                  |
| 8-DSL-R2 | TODO   | T25–T27 | DSL module restructure: new `dsl-api` + proper `@KotlinScript` host + executable DSL + E2E test | Current phase                                                     |

**Total Completed:** 24 tasks across 7 phases | **Current:** Phase 8 (DSL Restructure) | **Remaining:** T25 → T26a →
T26b → T26c → T26d → T27

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
T25 ──► T26a ──► T26b ──► T26c ──► T26d ──► T27
T01 ──────────────────────────────► T26d (Testcontainers Temporal)
```

---

## Next Recommended Session Start Order

Parallel-safe starting tasks (no blockers): **T25** first, then **T26a**.

---

## Phase 8 — DSL Restructure Detail

### Problem Summary

The LLM-generated DSL code (T04–T06) implemented a **manifest/registry pattern** (scripts call `registry.register(...)`
explicitly) instead of the **executable script pattern** the TDD requires (scripts are real Kotlin scripts with an
implicit receiver, lambdas execute immediately). Key gaps:

| Area                          | Generated (broken)                                             | Required                                                                                                    |
|-------------------------------|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| Script host                   | JSR-223 `ScriptEngineManager`                                  | `BasicJvmScriptingHost` + `@KotlinScript`                                                                   |
| Script entry point            | `registry.register(event(...) { })`                            | `event("CODE") { }` as top-level call on implicit receiver                                                  |
| `transactions {}`             | `transactions(vararg tx: TransactionDefinition)` — static list | `transactions { ctx -> ctx.step(...).then(...); ctx.await(...) }` — suspend lambda with `TransactionsScope` |
| `WorkflowBuilder` transitions | flat `transition(from, to, on, event, onFault)`                | closure `transitions { ctx -> FROM -> TO on Action.X { ... } onFault { ... } }`                             |
| `#import`                     | not implemented                                                | simple Kotlin `defaultImports` in compilation config (no custom pre-processor for v1)                       |
| `dsl-api` module              | does not exist — interfaces live inside `dsl`                  | separate Gradle module, Java/Kotlin interfaces shared by `dsl` and `backend`                                |
| E2E test                      | none                                                           | Testcontainers Temporal + stub transactions + real `.kts` file → assert execution trace                     |

### T25 — `dsl-api` Gradle module

**Goal:** Extract the shared contract (interfaces + records) into a new `dsl-api` module that both `dsl` and `backend`
depend on. Pure Java/Kotlin, no Spring, no scripting deps.

**What goes in `dsl-api`:**

- `Action` enum (PREVIEW, SUBMIT, APPROVE, REJECT, CANCEL, CLOSE, ROLLBACK)
- `Signal` + `SignalType`
- `ExecutionResult`
- `HelperFunction<I, O>` interface
- `TransactionDefinition` interface (with `preview`, `execute`, `rollback` as optional lambdas)
- `WorkflowDefinition` interface
- `EventDefinition` interface
- Context hierarchy: `BaseContext`, `ParameterContext`, `EnrichmentContext`, `TransactionContext`, `FinishContext`,
  `MassOperationContext`

**What stays in `dsl` (internal):**

- All builders (`EventBuilder`, `WorkflowBuilder`, `TransactionBuilder`, `MassOpBuilder`)
- `EventDslScope`, `TransactionsScope`, `StepHandle`, `ConditionalStepBuilder`
- `@KotlinScript` definition + `ScriptHost`
- `DslValidator`, `ValidationError`

**Files to create:**

- `dsl-api/build.gradle` — `kotlin("jvm")`, no scripting deps
- `dsl-api/src/main/kotlin/cbs/dsl/api/` — move all types from `dsl/src/main/kotlin/cbs/dsl/api/`
- `settings.gradle` — add `include("dsl-api")`

### T26a — `@KotlinScript` host (replace JSR-223)

**Goal:** Replace `DslScriptHost` (JSR-223) with a proper `BasicJvmScriptingHost`-based host.

**Files to create/replace:**

- `dsl/src/main/kotlin/cbs/dsl/script/EventScript.kt` — `@KotlinScript(fileExtension = "event.kts", ...)` abstract class
  extending `EventDslScope`, constructor receives `DslExecutionContext`
- `dsl/src/main/kotlin/cbs/dsl/script/EventScriptCompilationConfiguration.kt` —
  `dependenciesFromCurrentContext(wholeClasspath = true)`, `defaultImports("cbs.dsl.api.*", "cbs.dsl.script.*")`
- `dsl/src/main/kotlin/cbs/dsl/script/ScriptHost.kt` — `BasicJvmScriptingHost`, `constructorArgs(context)`, returns
  `ScriptResult`
- Delete: `dsl/src/main/kotlin/cbs/dsl/compiler/DslScriptHost.kt`

### T26b — `EventDslScope` + `TransactionsScope`

**Goal:** Implement the executable DSL surface — `event{}`, `transactions { ctx -> ctx.step().then(); ctx.await() }`.

**Key types:**

- `EventDslScope` — abstract class, `event(code, block)` member function,
  `abstract val executionContext: DslExecutionContext`
- `TransactionsScope` — `suspend fun step(tx: TransactionDefinition): StepHandle`,
  `suspend fun step(block: ConditionalStepBuilder.() -> Unit): StepHandle`,
  `suspend fun await(vararg handles: StepHandle)`
- `StepHandle` — `suspend fun then(tx: TransactionDefinition): StepHandle`, `suspend fun join()`
- `ConditionalStepBuilder` — `when(predicate) then { transaction(...) } orWhen { } otherwise { }`
- `EventBuilder` — `context { ctx -> }`, `display { ctx -> }`, `transactions { ctx -> }` (suspend lambda),
  `finish { ctx, ex -> }`

### T26c — `WorkflowBuilder` closure transitions

**Goal:** Replace flat `transition(from, to, on, event, onFault)` with the DSL-spec closure syntax.

**Syntax target:**

```kotlin
workflow("LOAN_CONTRACT") {
  transitions { ctx ->
    DRAFT -> ENTERED on Action.SUBMIT {
    val a = ctx.runEvent(loanCreateAgreement)
    ctx.await(a)
  } onFault { ctx ->
    ctx.runEvent(loanFaultNotification)
  }
  }
}
```

**Key types:**

- `TransitionBuilder` — infix `on`, `onFault` extension, collects `TransitionRule` with closure
- `WorkflowBuilder` — `transitions(block: TransitionScope.() -> Unit)`, infers `states`/`initial`/`terminal` when not
  declared

### T26d — E2E test (Testcontainers + Temporal)

**Goal:** Full chain test: stub transactions + helpers in `dsl` test sources → write `loan-disbursement.event.kts` →
compile via `ScriptHost` → run → assert execution trace.

**Test structure:**

```
dsl/src/test/kotlin/cbs/dsl/e2e/
├── LoanDisbursementE2ETest.kt       ← @Testcontainers, spins up Temporal
├── stubs/
│   ├── StubTransactions.kt          ← KycCheck, CreditScoring, DebitFunding, etc.
│   └── StubHelpers.kt               ← FIND_CUSTOMER_CODE_BY_ID, LOAN_CONDITIONS_BY_ID, etc.
└── scripts/
    └── loan-disbursement.event.kts  ← real script using DSL syntax from TDD
```

**Assertions:**

- Script compiles without errors
- `context {}` block runs: `ctx["customerCode"]` is populated
- `transactions {}` block runs: all steps execute in declared order, `await()` barriers respected
- `finish {}` block runs: `SEND_DISBURSEMENT_NOTIFICATION` helper called with correct params
- Execution trace matches expected step sequence

**Testcontainers:** `temporal-test-server` image (or `io.temporal:temporal-testing` in-process server — prefer
in-process to avoid Docker dependency in unit test phase).

### T27 — Backend integration

**Goal:** Wire `backend` → `dsl-api`, `backend` → `dsl`. Remove all deprecated classes. Update `DevDslController` to use
new `ScriptHost`.

**Delete:**

- `dsl/src/main/kotlin/cbs/dsl/runtime/DslRegistry.kt`
- `dsl/src/main/kotlin/cbs/dsl/runtime/StubWorkflowActivity.kt`
- `dsl/src/main/kotlin/cbs/dsl/compiler/DslScriptHost.kt`
- `dsl/src/main/kotlin/cbs/dsl/compiler/GiteaRulesSource.kt`
- `dsl/src/main/kotlin/cbs/dsl/compiler/DslCompiler.kt`
- `dsl/src/main/kotlin/cbs/dsl/compiler/CompileDslMain.kt`

**Update:**

- `backend/build.gradle` — add `implementation project(':dsl-api')`, `runtimeOnly project(':dsl')`
- `DevDslController` — use new `ScriptHost` + `DslExecutionContext` with stub registry for dev mode
