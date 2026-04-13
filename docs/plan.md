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

| Module            | Type            | Purpose                                                              |
|-------------------|-----------------|----------------------------------------------------------------------|
| `backend`         | Spring Boot app | Entry point, security, OpenAPI, Temporal worker/client config        |
| `starter`         | Library JAR     | All domain features: entities, repos, services, controllers, Flyway  |
| `dsl`             | Library JAR     | Kotlin DSL api interfaces + runtime builders + compiler Gradle tasks |
| `client`          | Generated JAR   | Feign + TypeScript clients from OpenAPI                              |
| `frontend`        | Nuxt 3 SPA      | Admin UI (hexagonal, backend-first — Phase 7)                        |
| `frontend-plugin` | Nuxt layer      | Shared domain types, ports, Vue components                           |

**Dependency graph:** `backend → starter → (none)` · `backend → dsl → (none)` · `client → backend`

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

---

## Phase Details

---

### Phase 0 — Infrastructure Setup

#### T01 · Temporal + Temporal UI in Docker Compose

**Goal:** Temporal Server and Temporal Web UI running locally alongside PostgreSQL. Backend can connect on startup.

**Files to touch:**

- [`docker-compose.yml`](../docker-compose.yml) — add `temporal` and `temporal-ui` service includes
- `docker/temporal.yml` — new compose override file with services

**Services needed (standard Temporal local dev stack):**

```yaml
# docker/temporal.yml
services:
  temporal:
    image: temporalio/auto-setup:1.27
    ports: [ "7233:7233" ]
    environment:
      DB: postgresql
      DB_PORT: 5432
      POSTGRES_USER: cbsnova
      POSTGRES_PWD: cbsnova
      POSTGRES_SEEDS: postgres
    depends_on: [ postgres ]

  temporal-ui:
    image: temporalio/ui:2.34
    ports: [ "8080:8080" ]
    environment:
      TEMPORAL_ADDRESS: temporal:7233
    depends_on: [ temporal ]
```

**Acceptance:** `docker compose up -d` → Temporal UI reachable at `http://localhost:8080`, namespace `default` visible.

**Verification steps:**

1. `docker compose ps` — all containers Running
2. `curl http://localhost:8080` — returns HTML
3. `docker logs cbs-nova-temporal-1` — no fatal errors

---

#### T02 · Gitea in Docker Compose + examples/cbs-rules

**Goal:** Gitea instance running at `localhost:3001`. Create `examples/cbs-rules/` in this repo as placeholder DSL
files.
Backend CI-style `compileDsl` task clones from Gitea.

**Files to touch:**

- [`docker-compose.yml`](../docker-compose.yml) — add Gitea include
- `docker/gitea.yml` — new compose override file
- `examples/cbs-rules/` — create folder structure with placeholder `.kts` files

**Gitea compose:**

```yaml
# docker/gitea.yml
services:
  gitea:
    image: gitea/gitea:1.22
    ports:
      - "3001:3000"
      - "2222:22"
    volumes: [ gitea-data:/data ]
    environment:
      USER_UID: 1000
      USER_GID: 1000
volumes:
  gitea-data:
```

**Placeholder DSL structure (`examples/cbs-rules/`):**

```
examples/cbs-rules/
├── global/
│   └── banking-helpers.helper.kts    # stub: only comments + TODO
├── loan-disbursement/
│   ├── loan-disbursement.event.kts   # stub event DSL
│   └── debit-funding-account.transaction.kts
├── loan-contract.workflow.kts        # stub workflow DSL
└── mass-operations/
    └── interest-charge/
        └── interest-charge.mass.kts  # stub mass op DSL
```

**Acceptance:** Gitea at `http://localhost:3001`. `examples/cbs-rules/` committed. README in `examples/` explains how to
push to Gitea.

---

#### T03 · Create `dsl` Gradle Module (Kotlin)

**Goal:** New `dsl` Gradle submodule added to the multi-project build. Compiles Kotlin. Depended on by `backend`.

**Files to create/modify:**

- [`settings.gradle`](../settings.gradle) — add `include 'dsl'` (line 6 after `include 'client'`)
- `dsl/build.gradle` — new file
- `dsl/src/main/kotlin/.gitkeep` — placeholder

**`dsl/build.gradle` skeleton:**

```groovy
plugins {
  id 'org.jetbrains.kotlin.jvm'
}

dependencies {
  // Kotlin stdlib already managed via kotlin-jvm plugin
  implementation libs.kotlin.stdlib
  implementation libs.kotlin.reflect
  implementation libs.kotlinx.coroutines.core
}

java {
  toolchain {languageVersion = JavaLanguageVersion.of(25)}
}
```

**[`backend/build.gradle`](../backend/build.gradle)** — add `implementation project(':dsl')`.

**Acceptance:** `./gradlew :dsl:build` succeeds (empty module). `./gradlew :backend:build` still works.

---

### Phase 1 — DSL Foundation

#### T04 · DSL API: Kotlin Interfaces & Context Types

**Goal:** All Kotlin interfaces/data classes that define the DSL contract. No implementation — pure API.

**Package:** `cbs.dsl.api` inside `dsl/src/main/kotlin/cbs/dsl/api/`

**Files to create (ref: [arch/module-structure.md](arch/module-structure.md) lines
52-82, [arch/dsl-design.md](arch/dsl-design.md)):**

| File                              | Content                                                                                                                                                                     |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Action.kt`                       | `enum class Action { PREVIEW, SUBMIT, APPROVE, REJECT, CANCEL, CLOSE, ROLLBACK }`                                                                                           |
| `SignalType.kt`                   | `enum class SignalType { PARTIAL, COMPLETED }`                                                                                                                              |
| `Signal.kt`                       | `data class Signal(val source: String, val type: SignalType, val payload: Map<String,Any>)` + companion factory methods                                                     |
| `HelperInput.kt`                  | Marker interface                                                                                                                                                            |
| `HelperOutput.kt`                 | Marker interface                                                                                                                                                            |
| `HelperFunction.kt`               | `interface HelperFunction<I : HelperInput, O : HelperOutput> { fun execute(input: I): O }`                                                                                  |
| `context/BaseContext.kt`          | Base context with `eventCode`, `workflowExecutionId`, `performedBy`, `dslVersion`                                                                                           |
| `context/ParameterContext.kt`     | Extends BaseContext; holds `eventParameters: Map<String,Any>`                                                                                                               |
| `context/EnrichmentContext.kt`    | Extends ParameterContext; mutable enrichment map                                                                                                                            |
| `context/TransactionContext.kt`   | Extends EnrichmentContext; `isResumed: Boolean`, `prolong(action: Action)`                                                                                                  |
| `context/FinishContext.kt`        | Final context after transactions; `displayData: Map<String,Any>`                                                                                                            |
| `context/MassOperationContext.kt` | MassOp-specific context; `itemKey`, `itemData`, `massOpExecutionId`                                                                                                         |
| `WorkflowDefinition.kt`           | `interface WorkflowDefinition { val code: String; val states: List<String>; val initial: String; val terminalStates: List<String>; val transitions: List<TransitionRule> }` |
| `TransitionRule.kt`               | `data class TransitionRule(val from: String, val to: String, val on: Action, val event: EventDefinition, val onFault: String = "FAULTED")`                                  |
| `EventDefinition.kt`              | `interface EventDefinition { val code: String; context block; display block; transactions block; finish block }`                                                            |
| `TransactionDefinition.kt`        | `interface TransactionDefinition { val code: String; preview/execute/rollback: (TransactionContext)->Unit }`                                                                |
| `HelperDefinition.kt`             | `interface HelperDefinition<I,O> : HelperFunction<I,O> { val code: String }`                                                                                                |
| `ConditionDefinition.kt`          | `interface ConditionDefinition { val code: String; val predicate: (TransactionContext)->Boolean }`                                                                          |
| `MassOperationDefinition.kt`      | Full MassOp definition (source, triggers, lock, context, item closure, signals)                                                                                             |
| `TriggerDefinition.kt`            | Sealed class: CronTrigger, OnceTrigger, EveryTrigger, SignalTrigger                                                                                                         |
| `SourceDefinition.kt`             | `interface SourceDefinition { fun load(ctx: MassOperationContext): List<Map<String,Any>> }`                                                                                 |
| `LockDefinition.kt`               | `interface LockDefinition { fun isLocked(ctx: MassOperationContext): Boolean }`                                                                                             |
| `ItemDefinition.kt`               | `interface ItemDefinition { fun execute(ctx: MassOperationContext) }`                                                                                                       |
| `ExecutionResult.kt`              | `data class ExecutionResult(val status: String, val eventExecutionId: Long, val workflowExecutionId: Long)`                                                                 |

**Key design rule:** No Spring, no Temporal, no DB dependencies in `dsl` module — pure Kotlin.

**Acceptance:** `./gradlew :dsl:build` compiles. No external dependencies beyond kotlin-stdlib and kotlinx-coroutines.

---

#### T05 · DSL Runtime: Builders, Registry, Stub Workflow

**Goal:** Kotlin builder DSL that `.kts` authors use. Implements the API interfaces from T04 via builders.

**Package:** `cbs.dsl.runtime` inside `dsl/src/main/kotlin/cbs/dsl/runtime/`

**Files (ref: [arch/module-structure.md](arch/module-structure.md) lines 84-96):**

| File                       | Content                                                                                                                        |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `WorkflowBuilder.kt`       | `fun workflow(code: String, block: WorkflowBuilder.()->Unit): WorkflowDefinition`                                              |
| `EventBuilder.kt`          | `fun event(code: String, block: EventBuilder.()->Unit): EventDefinition`                                                       |
| `TransactionBuilder.kt`    | `fun transaction(code: String, block: TransactionBuilder.()->Unit): TransactionDefinition`                                     |
| `HelperBuilder.kt`         | `fun helper(code: String, block: HelperBuilder.()->Unit): HelperDefinition<*,*>`                                               |
| `ConditionBuilder.kt`      | `fun condition(code: String, predicate: (TransactionContext)->Boolean): ConditionDefinition`                                   |
| `MassOperationBuilder.kt`  | `fun massOperation(code: String, block: MassOperationBuilder.()->Unit): MassOperationDefinition`                               |
| `SignalBuilder.kt`         | Emits Signal objects from MassOp definitions                                                                                   |
| `TriggerBuilder.kt`        | `cron(expr)`, `once(at)`, `every(days/weeks)`, `onSignal(Signal)`                                                              |
| `StubWorkflowGenerator.kt` | Generates a stub `WorkflowDefinition` for events without explicit workflow DSL                                                 |
| `ConditionDsl.kt`          | `when/then/orWhen/otherwise` combinators for transaction condition blocks                                                      |
| `StepHandle.kt`            | `.then()` chaining for sequential transaction steps                                                                            |
| `DslRegistry.kt`           | Singleton registry: `register(WorkflowDefinition)`, `register(EventDefinition)`, etc.; `findWorkflow(code)`, `findEvent(code)` |

**Acceptance:** Unit tests for WorkflowBuilder and EventBuilder producing correct definitions.

---

#### T06 · DSL Compiler: Gradle Tasks + Semantic Validator

**Goal:** Gradle tasks in `dsl/build.gradle` that download `.kts` from Gitea, compile them to a JAR, and validate
semantics.
Also: the dev-mode JSR-223 evaluator as a Spring `@Service` for use by T20.

**Gradle tasks (in `dsl/build.gradle`):**

| Task          | Purpose                                                                                   |
|---------------|-------------------------------------------------------------------------------------------|
| `downloadDsl` | Clone/pull `cbs-rules` branch from Gitea (`GITEA_URL`, `DSL_BRANCH` env vars)             |
| `compileDsl`  | Compile all `.kts` files via Kotlin scripting API → `dsl-rules-{version}.jar`             |
| `validateDsl` | Semantic validation: all referenced events/helpers/states exist; fails build on violation |

**Files to create:**

- `dsl/src/main/kotlin/cbs/dsl/compiler/KtsCompiler.kt` — JSR-223 + Kotlin scripting API
- `dsl/src/main/kotlin/cbs/dsl/compiler/SemanticValidator.kt` — validates registry after compile
- `dsl/src/main/kotlin/cbs/dsl/compiler/ImportResolver.kt` — resolves `#import` declarations
- `dsl/src/main/kotlin/cbs/dsl/compiler/DslDevEvaluator.kt` — Spring `@Service` for runtime eval (used by T20)

**Validation rules (ref: [arch/build-deploy.md](arch/build-deploy.md)):**

1. All events referenced in `transitions {}` exist in registry
2. All helpers referenced in `context {}` and `transactions {}` blocks resolve
3. All transition target states declared in workflow `states`
4. All condition references resolve
5. `Signal.from("OP_CODE", ...)` references a known mass operation code

**Acceptance:** `./gradlew :dsl:compileDsl` compiles `examples/cbs-rules/` stubs. `./gradlew :dsl:validateDsl` runs
without errors on placeholder files.

---

### Phase 2 — Database Schema

#### T07 · Flyway: Core Tables

**Goal:** Three migrations in `starter/src/main/resources/db/migration/` for core workflow tracking.

**Files to create:**

- `starter/src/main/resources/db/migration/V20260501000000__create_workflow_execution.sql`
- `starter/src/main/resources/db/migration/V20260501000001__create_event_execution.sql`
- `starter/src/main/resources/db/migration/V20260501000002__create_workflow_transition_log.sql`

**Full SQL from [arch/state-management.md](arch/state-management.md) lines 10-63:**

- `workflow_execution`: id BIGSERIAL PK, workflow_code, dsl_version, current_state, status (ACTIVE/CLOSED/FAULTED),
  context JSONB, display_data JSONB, performed_by, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ + 3 indexes
- `event_execution`: id, event_code, dsl_version, action, status, context JSONB, executed_transactions JSONB,
  temporal_workflow_id, workflow_execution_id FK, performed_by, timestamps + 2 indexes
- `workflow_transition_log`: id, workflow_execution_id FK, event_execution_id FK, action, from_state, to_state, status (
  RUNNING/COMPLETED/FAULTED), fault_message TEXT, dsl_version, performed_by, created_at, completed_at + 2 indexes

**Acceptance:** `./gradlew :backend:bootRun` starts without Flyway errors. Tables visible in PostgreSQL.

---

#### T08 · Flyway: Mass Operation Tables

**Goal:** Two migrations for mass operation tracking.

**Files to create:**

- `starter/src/main/resources/db/migration/V20260501000010__create_mass_operation_execution.sql`
- `starter/src/main/resources/db/migration/V20260501000011__create_mass_operation_item.sql`

**Full SQL from [arch/state-management.md](arch/state-management.md) lines 64-end:**

- `mass_operation_execution`: id, code, category, dsl_version, status (RUNNING/DONE/DONE_WITH_FAILURES/LOCKED/FAULTED),
  context JSONB, total_items BIGINT, processed_count BIGINT, failed_count BIGINT, trigger_type, trigger_source,
  performed_by, started_at, completed_at, temporal_workflow_id
- `mass_operation_item`: id, mass_operation_execution_id FK, item_key VARCHAR(200), item_data JSONB (encrypted),
  status (PENDING/RUNNING/DONE/FAILED), error_message TEXT, event_execution_id FK nullable, retry_of BIGINT FK
  nullable (self-ref), created_at, updated_at

**Acceptance:** `./gradlew :backend:bootRun` — all migrations applied. Both tables visible.

---

### Phase 3 — Core Orchestration Engine

#### T09 · Temporal Client + Worker Config in `backend`

**Goal:** Spring beans for Temporal `WorkflowClient` and `WorkerFactory`. Worker registered with `EventWorkflow` and
`TransactionActivity` task queues.

**Files to create in `backend/src/main/java/cbs/app/config/`:**

- `TemporalConfig.java` — `@Configuration` providing `WorkflowClient`, `WorkerFactory`, `Worker` beans
- `TemporalProperties.java` — `@ConfigurationProperties("temporal")` with `host`, `port`, `namespace`, `taskQueue`

**`backend/src/main/resources/application.yml`** additions:

```yaml
temporal:
  host: localhost
  port: 7233
  namespace: default
  task-queue: cbs-nova-events
```

**Dependencies to add to `gradle/libs.versions.toml`:**

```toml
temporal-sdk = { module = "io.temporal:temporal-sdk", version = "1.28.0" }
```

**Worker registered for task queues:**

- `cbs-nova-events` — for `EventWorkflowImpl`, `TransactionActivityImpl`
- `cbs-nova-mass-ops` — for `MassOpWorkflowImpl`, `MassOpItemActivityImpl`

**Acceptance:** Backend starts with Temporal connected. No errors in logs. Temporal UI at `localhost:8080` shows
namespace `default` with 0 workflows.

---

#### T10 · EventWorkflow + TransactionActivity (Temporal)

**Goal:** Temporal workflow and activity implementations that execute events and run transactions.

**Files to create in `backend/src/main/java/cbs/app/temporal/`:**

| File                           | Content                                                                                                                                      |
|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `EventWorkflow.java`           | `@WorkflowInterface` with `execute(EventExecutionRequest req): ExecutionResult`                                                              |
| `EventWorkflowImpl.java`       | `@WorkflowImpl` — calls `TransactionActivity` via stub for each transaction; updates `workflow_execution` state via `ExecutionContextBridge` |
| `TransactionActivity.java`     | `@ActivityInterface` with `preview(...)`, `execute(...)`, `rollback(...)` methods                                                            |
| `TransactionActivityImpl.java` | `@ActivityImpl` — delegates to `TransactionDefinition` from `DslRegistry`; calls `ContextEncryptionService`                                  |
| `ExecutionContextBridge.java`  | Adapts Temporal workflow context to `TransactionContext` DSL interface                                                                       |

**Key behaviors:**

- `EventWorkflowImpl` uses `CompletablePromise` for parallel transaction execution
- On activity failure → attempt `rollback()` → update `workflow_execution.status = FAULTED`
- `ctx.prolong(action)` in `finish {}` → signal next transition from within workflow
- Temporal workflow ID format: `{eventCode}-{eventNumber}-{dslVersion}` (ref: [arch/versioning.md](arch/versioning.md))

**Acceptance:** Unit test with `TestWorkflowEnvironment` that runs a stub event through the workflow and verifies state
transitions.

---

#### T12 · State Repos + Entities (must be done before T11)

**Goal:** JPA entities and Spring Data repositories for `workflow_execution`, `event_execution`,
`workflow_transition_log`.

**Files in `starter/src/main/java/cbs/nova/` (following existing pattern in starter):**

| File                                              | Content                                                                             |
|---------------------------------------------------|-------------------------------------------------------------------------------------|
| `entity/WorkflowExecutionEntity.java`             | `@Entity @Table("workflow_execution")` — all columns as fields                      |
| `entity/EventExecutionEntity.java`                | `@Entity @Table("event_execution")` — FK to WorkflowExecutionEntity                 |
| `entity/WorkflowTransitionLogEntity.java`         | `@Entity @Table("workflow_transition_log")`                                         |
| `repository/WorkflowExecutionRepository.java`     | `JpaRepository<WorkflowExecutionEntity, Long>` + `findByWorkflowCodeAndStatus(...)` |
| `repository/EventExecutionRepository.java`        | `JpaRepository<EventExecutionEntity, Long>` + `findByWorkflowExecutionId(Long)`     |
| `repository/WorkflowTransitionLogRepository.java` | `JpaRepository<...>` + `findByWorkflowExecutionIdOrderByCreatedAtAsc(Long)`         |

**Note:** JSONB fields `context` and `display_data` stored as `String` at JPA level, encrypted/decrypted by
`ContextEncryptionService` (T11) before read/write.

**Acceptance:** Entities detected by `NovaAutoConfiguration` `@EntityScan`. `./gradlew :backend:integrationTest` passes
with Testcontainers.

---

#### T11 · Core Services

**Goal:** Business logic layer between controller and Temporal. Services live in `starter` (auto-configured).

**Files in `starter/src/main/java/cbs/nova/service/`:**

| File                            | Responsibility                                                                                      |
|---------------------------------|-----------------------------------------------------------------------------------------------------|
| `EventService.java`             | Entry point: validate input, resolve workflow, check transition, call `WorkflowExecutor`            |
| `WorkflowResolver.java`         | Load `WorkflowDefinition` from `DslRegistry`; find applicable transition for `(state, action)` pair |
| `WorkflowExecutor.java`         | Start or signal Temporal workflow; persist `event_execution` and `workflow_transition_log` rows     |
| `ContextEvaluator.java`         | Evaluate `context {}` block from `EventDefinition`; returns `Map<String,Any>`                       |
| `ContextEncryptionService.java` | AES-GCM encrypt/decrypt of JSONB strings; key from `application.yml` property                       |
| `DslVersionService.java`        | Returns current `dsl_version` string from JAR manifest (`Implementation-Version`)                   |

**`EventService.execute(EventExecutionRequest)` flow:**

1. Resolve `WorkflowDefinition` (or stub) from `DslRegistry`
2. If `action == PREVIEW` → evaluate `context {}` only, return preview result, no Temporal
3. Load `workflow_execution` by `eventNumber` (if provided) — verify current state allows transition
4. Evaluate `context {}` → encrypt → start/signal Temporal workflow
5. Persist `event_execution` row with `status = RUNNING`
6. Return `ExecutionResult` with IDs

**Acceptance:** Unit tests for each service class. `WorkflowResolver` test covers: valid transition, invalid
transition (throws), stub workflow generation.

---

#### T13 · EventController: POST /api/events/execute

**Goal:** Single HTTP endpoint that accepts event execution requests and delegates to `EventService`.

**File:** `backend/src/main/java/cbs/app/controller/EventController.java`

**Request DTO:** `EventExecutionRequest { code, action, eventNumber?, eventParameters: Map<String,Object> }`
**Response DTO:** `EventExecutionResponse { eventExecutionId, workflowExecutionId, status, currentState, displayData? }`

**Error codes (ref: [arch/api-contract.md](arch/api-contract.md)):**

- `400 INVALID_TRANSITION` — action not valid for current state
- `400 MISSING_PARAMETERS` — required context parameters absent
- `422 CONTEXT_FAULT` — context evaluation failed

**Also create:**

- `backend/src/main/java/cbs/app/dsl/DslLoader.java` — `@Component` that loads `DslRegistry` from classpath JAR on
  startup via `@PostConstruct`

**Acceptance:** `@WebMvcTest` test for `EventController`. Manual `curl` test with a stub DSL event via
`POST /api/events/execute`.

---

### Phase 4 — Mass Operation

#### T17 · MassOp Repos + Entities (must be done before T15)

**Goal:** JPA entities and repositories for mass operation tables.

**Files in `starter/src/main/java/cbs/nova/`:**

| File                                               | Content                                                                                           |
|----------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `entity/MassOperationExecutionEntity.java`         | All columns from migration T08                                                                    |
| `entity/MassOperationItemEntity.java`              | FK to execution, `retry_of` self-ref FK                                                           |
| `repository/MassOperationExecutionRepository.java` | + `findByCodeAndStatus(...)`, `findByTemporalWorkflowId(...)`                                     |
| `repository/MassOperationItemRepository.java`      | + `findByMassOperationExecutionIdAndStatus(...)`, `countByMassOperationExecutionIdAndStatus(...)` |

**Acceptance:** Integrated into `NovaAutoConfiguration` `@EntityScan`. Integration test verifies persistence.

---

#### T14 · MassOpWorkflow + MassOpItemActivity (Temporal)

**Goal:** Temporal workflow that fans out over all items and an activity that processes one item.

**Files in `backend/src/main/java/cbs/app/temporal/`:**

| File                          | Content                                                                                               |
|-------------------------------|-------------------------------------------------------------------------------------------------------|
| `MassOpWorkflow.java`         | `@WorkflowInterface` with `start(MassOpExecutionRequest)`                                             |
| `MassOpWorkflowImpl.java`     | Fan-out: for each item spawn `MassOpItemActivity` as async `Promise`; aggregate results; emit signals |
| `MassOpItemActivity.java`     | `@ActivityInterface` with `processItem(MassOpItemRequest): ItemResult`                                |
| `MassOpItemActivityImpl.java` | Delegates to `ItemDefinition.execute()` from `DslRegistry`; updates item status in DB                 |

**Key behaviors:**

- Fan-out via `Async.function(activity::processItem, req)` for each item
- Items failing don't stop the batch — catch exception, mark FAILED, continue
- After every N items processed → emit `PARTIAL` signal (N configurable per `MassOperationDefinition`)
- After all items → emit `COMPLETED` signal via `SignalEmitter`

**Acceptance:** Test with `TestWorkflowEnvironment`, 3 items (1 failing), verify DONE_WITH_FAILURES status and signal
emission.

---

#### T15 · MassOp Services

**Files in `starter/src/main/java/cbs/nova/service/`:**

| File                          | Responsibility                                                                          |
|-------------------------------|-----------------------------------------------------------------------------------------|
| `MassOperationService.java`   | Trigger, status query, item list, item retry; persists `mass_operation_execution` rows  |
| `MassOperationScheduler.java` | `@Scheduled` cron-based triggers; evaluates `lock {}` closure before starting           |
| `SignalEmitter.java`          | Sends PARTIAL/COMPLETED signals to Temporal workflows listening via `onSignal` triggers |

**`MassOperationService.trigger(code, context?)` flow:**

1. Load `MassOperationDefinition` from `DslRegistry`
2. Evaluate `lock {}` — if returns `true` → persist `status=LOCKED`, return
3. Evaluate `source {}` → get item list → persist `mass_operation_execution` with `total_items`
4. Persist one `mass_operation_item` row per item (status=PENDING)
5. Start `MassOpWorkflow` via Temporal client

**Acceptance:** Unit tests. Scheduler test verifies cron-triggered execution with lock evaluation.

---

#### T16 · MassOperationController

**File:** `backend/src/main/java/cbs/app/controller/MassOperationController.java`

**Endpoints (ref: [arch/api-contract.md](arch/api-contract.md)):**

| Method | Path                                                      | Description                     |
|--------|-----------------------------------------------------------|---------------------------------|
| `POST` | `/api/mass-operations/trigger`                            | Body: `{ code, context? }`      |
| `GET`  | `/api/mass-operations/{executionId}`                      | Status + counts                 |
| `GET`  | `/api/mass-operations/{executionId}/items`                | `?status=FAILED&page=0&size=50` |
| `POST` | `/api/mass-operations/{executionId}/items/{itemId}/retry` | Creates new item row, re-runs   |

**Acceptance:** `@WebMvcTest` tests for all 4 endpoints. `LOCKED` response when lock returns true.

---

### Phase 5 — BPMN Export

#### T18 · StaticBpmnGenerator + BpmnExporter

**Goal:** Generate BPMN 2.0 XML from `WorkflowDefinition`. No frontend yet.

**Files in `starter/src/main/java/cbs/nova/bpmn/`:**

| File                       | Content                                                                  |
|----------------------------|--------------------------------------------------------------------------|
| `BpmnExporter.java`        | Facade: `exportStatic(workflowCode): String` → XML                       |
| `StaticBpmnGenerator.java` | Maps `WorkflowDefinition` → BPMN 2.0 XML string using DOM/string builder |
| `BpmnDiagramLayout.java`   | Simple left-to-right auto-layout for states (no external lib)            |

**BPMN mapping (ref: [arch/bpmn-export.md](arch/bpmn-export.md)):**

- Non-terminal states → `<userTask>`
- Terminal states → `<endEvent>`
- `FAULTED` state → `<boundaryErrorEvent>`
- Transitions → `<sequenceFlow>`

**Acceptance:** Unit test: loan-contract workflow → produces valid BPMN XML string with correct task/flow counts.

---

#### T19 · BpmnController

**File:** `backend/src/main/java/cbs/app/controller/BpmnController.java`

**Endpoints:**

| Method | Path                         | Response                            |
|--------|------------------------------|-------------------------------------|
| `GET`  | `/api/workflows/{code}/bpmn` | `application/xml` — static template |

**Acceptance:** `@WebMvcTest` — valid XML returned for known workflow code. 404 for unknown code.

---

### Phase 6 — Dev DSL Mode

#### T20 · DevDslController

**Goal:** `@Profile("dev")` endpoint that evaluates inline `.kts` DSL content via JSR-223 without CI compile.

**File:** `backend/src/main/java/cbs/app/controller/DevDslController.java`

**Endpoint:** `POST /dev/dsl/execute` — Body:
`{ dslContent: String, eventCode: String, action: String, parameters: Map }`

**Flow:**

1. Pass `dslContent` to `DslDevEvaluator.evaluate(dslContent)` (from T06)
2. Registers definitions into a temporary `DslRegistry`
3. Delegates to `EventService` with the temp registry
4. Returns full `ExecutionResult`

**Acceptance:** Integration test with `@ActiveProfiles("dev")`. Inline stub `.kts` event executes end-to-end.

---

### Phase 7 — Frontend (Backend-First Gate)

> **Gate:** All of T01–T20 must be DONE before starting Phase 7.

#### T21 · Frontend: Execution List + Detail Page

**Pattern:** Hexagonal — follows same structure as Settings feature.
**Ref:** [ui/orchestration-ui.md](ui/orchestration-ui.md), CLAUDE.md §10-§16

**Files:**

- `frontend-plugin/composables/execution/WorkflowExecution.ts` — domain type
- `frontend-plugin/composables/execution/WorkflowExecutionRepository.ts` — port
- `frontend-plugin/composables/execution/ExecutionProvider.ts` — piqure DI
- `frontend-plugin/composables/execution/ExecutionListVue.vue` — presentational list
- `frontend/src/app/execution/infrastructure/secondary/WorkflowExecutionHttp.ts` — HTTP adapter
- `frontend/src/app/execution/infrastructure/primary/ExecutionListPageVue.vue` — page
- `frontend/src/app/execution/infrastructure/primary/ExecutionDetailPageVue.vue` — detail
- `frontend/src/app/execution/application/ExecutionRouter.ts` — routes
- `frontend/src/app/plugins/execution.ts` — DI wiring

---

#### T22 · Frontend: BPMN Viewer

**Dependencies:** `bpmn-js` npm package.
**Ref:** [arch/bpmn-export.md](arch/bpmn-export.md), [ui/orchestration-ui.md](ui/orchestration-ui.md)

- `frontend-plugin/composables/bpmn/BpmnViewer.vue` — wraps `bpmn-js` viewer
- Embedded in `ExecutionDetailPageVue.vue`
- Loads XML from `GET /api/workflows/{code}/bpmn`

---

#### T23 · Frontend: MassOperation Report UI

**Ref:** [ui/orchestration-ui.md](ui/orchestration-ui.md) §MassOperation report

- Summary card: code, category, trigger type, DSL version, start/end, counts + progress bar
- Filterable item list (ALL/DONE/FAILED/RUNNING/PENDING)
- Per-item error message + retry button
- Drill-down link to `ExecutionDetailPageVue`

---

#### T24 · Frontend: Navigation ABAC

**Ref:** [ui/navigation-abac.md](ui/navigation-abac.md), CLAUDE.md §12

- Route guard: check JWT role claims
- Sidebar items hidden/shown per role (ADMIN vs USER)
- `navigation-abac` feature plugin in `frontend/src/app/plugins/`

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
```

---

## Next Recommended Session Start Order

Parallel-safe starting tasks (no blockers): **T01, T02, T03, T07, T08** — all can be delegated simultaneously.
