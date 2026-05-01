# Technical Design Document: CBS-Nova Business Orchestration Engine

**Version:** v0.7.0 | **Date:** 2026-04-29 | **Status:** For team review

**Stack:** Java 25 · Spring Boot · Temporal · PostgreSQL · Java DSL · Gradle 9.x · Vue 3 + Nuxt 3 (admin UI)

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Core Entities](#3-core-entities)
4. [Sub-documents](#4-sub-documents)

---

## 1. Overview & Goals

CBS-Nova replaces a Spring-bean orchestration engine with Temporal + PostgreSQL for state and a **Java DSL** for
business-editable rules. Non-developers author rules in `.java` DSL files; the engine either generates Temporal
workflows/activities from them (production) or executes them via reflection (development).

**Key goals:**

- Business autonomy — analysts write/modify DSL without developer involvement
- Correctness — every instance runs to completion on the DSL version it started with
- Parallelism — all steps run as Temporal `CompletablePromise`s; `await()` marks sync points
- Full observability — every execution produces a `workflow_execution` artifact (context, display data, transition log)
- Resilience — per-item failure isolation in batch operations; failed items are re-runnable
- Unified model — workflowless events use an auto-generated stub workflow; `workflow_execution_id` is never null

**v0.7 pivot:** Kotlin Script (.kts) is abandoned in favor of a Java DSL with dual execution modes:
- **Production:** 3-layer compile-time code generation → Temporal workflows/activities → Spring beans
  - Layer 1: `@DslComponent` `*Function` → `*Definition` wrappers + SPI registration
  - Layer 2: `.java` DSL files → `EventDefinition` / `WorkflowDefinition` / `MassOperationDefinition`
  - Layer 3: `*Definition` → Temporal workflow/activity classes
- **Development:** Layer 1 + Layer 2 compile normally; Layer 3 replaced by generic `ReflectiveWorkflow` / `ReflectiveActivity` wrappers

**v0.5 additions:** MassOperation (batch orchestration with signal-driven chaining) + refined transitions (multi-event
closures, `ctx.runEvent()` / `ctx.resumeEvent()` / `ctx.await()`).

**Out of scope (v1):** typed DSL annotation processor, Nexus publishing, stakeholder funnel/heatmap UI, Temporal UI
iframe, mass op item export, `DRY_RUN` / `COMPENSATE` / `SUSPEND` / `REACTIVATE` actions, dynamic BPMN heatmap.

---

## 2. High-Level Architecture

```
Client / API Gateway
  │  POST /api/events/execute
  │  POST /api/mass-operations/trigger
  ▼
Spring Boot Application
  EventController / EventService / WorkflowResolver / WorkflowExecutor / ContextEvaluator
  MassOperationController / MassOperationService / MassOperationScheduler / SignalEmitter
  ContextEncryptionService
  └─ DSL Runtime (WorkflowDefinition, EventDefinition, TransactionDefinition,
                  HelperDefinition, ConditionDefinition, MassOperationDefinition)
  │                                    │
  ▼                                    ▼
Temporal Server                    PostgreSQL
  EventWorkflow                      workflow_execution   (JSONB, encrypted)
  TransactionActivity                event_execution      (JSONB, encrypted)
  MassOpWorkflow                     workflow_transition_log
  MassOpItemActivity                 mass_operation_execution
                                     mass_operation_item

Vue + Nuxt.js Admin Panel
  Execution detail + BPMN viewer · MassOperation report · Temporal UI link · cbs-rules VSCode link

Gitea (cbs-rules) → GitLab CI/Jenkins → Docker deploy
MQ / Webhook → external triggers
```

Temporal holds **only PKs**. All state lives in PostgreSQL. `context {}` is evaluated by Spring before any Temporal
call — bad input is rejected before a workflow instance is created.

Request flow: `Browser → Vite (9000) → Nuxt BFF (3000) → Backend (7070)`

### DSL Execution Modes

| Mode        | Environment                          | Mechanism                                                                 | Artifact                    |
|-------------|--------------------------------------|---------------------------------------------------------------------------|-----------------------------|
| `GENERATED` | production / CI / non-dev backend    | 3-layer codegen: Function→Definition, DSL→Definition, Definition→Temporal | Compiled workflow/activity  |
| `REFLECTED` | development only (`@Profile("dev")`) | Layer 1+2 compile; Layer 3 replaced by reflective wrappers                | Generic interpreter wrapper |

---

## 3. Core Entities

| Entity            | Description                                                                                                                                             |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Workflow**      | State machine: `states`, `initial`, `terminalStates`, `transitions`. All fields optional (inferred).                                                    |
| **Event**         | Triggered operation: `context {}`, `display {}`, `transactions {}`, `finish {}`. Standalone → stub workflow.                                            |
| **Transaction**   | Temporal Activity. Code: `TransactionFunction<I,O>` with `@DslComponent` → generated `TransactionDefinition`. Rollback is a compensating ledger entry.  |
| **Helper**        | Reusable computation. Code: `HelperFunction<I,O>` with `@DslComponent` → generated `HelperDefinition`. Used in `context {}` and `transactions {}`.      |
| **Condition**     | Reusable boolean predicate. Code: `ConditionFunction<I,O>` with `@DslComponent` → generated `ConditionDefinition`. Referenced in `when/then/otherwise`. |
| **MassOperation** | Batch orchestration: data source, triggers, business lock, per-item execution, PARTIAL/COMPLETED signals.                                               |

**Action enum:** `PREVIEW` · `SUBMIT` · `APPROVE` · `REJECT` · `CANCEL` · `CLOSE` · `ROLLBACK`

**Signal types:** `PARTIAL` (after every N items) · `COMPLETED` (after all items)

**Version format:** `{semver}-{gitCommitShort}` — locked at instance creation, never changes mid-run.

---

## 4. Sub-documents

### Architecture

| Document                                                 | Contents                                                                                                      |
|----------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| [arch/dsl-design.md](arch/dsl-design.md)                 | Full DSL reference, entity interfaces, `.java` file/folder conventions, code generation vs reflection         |
| [arch/execution-model.md](arch/execution-model.md)       | Execution flow, `runEvent` vs `resumeEvent`, context hierarchy                                                |
| [arch/workflow-lifecycle.md](arch/workflow-lifecycle.md) | State machine concepts, `prolong()`, example lifecycle                                                        |
| [arch/mass-operation.md](arch/mass-operation.md)         | Full MassOperation model, lock, triggers, signals, retry                                                      |
| [arch/state-management.md](arch/state-management.md)     | Full PostgreSQL schema with indexes                                                                           |
| [arch/build-deploy.md](arch/build-deploy.md)             | Gitea strategy, CI/CD steps, dev mode endpoint                                                                |
| [arch/api-contract.md](arch/api-contract.md)             | Full request/response shapes for all endpoints                                                                |
| [arch/versioning.md](arch/versioning.md)                 | Strict isolation, Temporal workflow ID format, worker drain                                                   |
| [arch/bpmn-export.md](arch/bpmn-export.md)               | Static and dynamic BPMN, `bpmn-js` frontend integration                                                       |
| [arch/module-structure.md](arch/module-structure.md)     | Full Gradle module file tree (`app`, `dsl-api`, `dsl`, `temporal-core`, `bpmn-export`, `mass-operation-core`) |
| [arch/risks.md](arch/risks.md)                           | Risk register, resolved decisions, open questions                                                             |

### UI

| Document                                           | Contents                                                |
|----------------------------------------------------|---------------------------------------------------------|
| [ui/overview.md](ui/overview.md)                   | Goals, repo structure, PNPM monorepo layout             |
| [ui/nuxt-bff.md](ui/nuxt-bff.md)                   | Nuxt as BFF: routes, middleware, error shape            |
| [ui/package-breakdown.md](ui/package-breakdown.md) | admin-core, admin-ui, admin-components, admin-codegen   |
| [ui/crud-system.md](ui/crud-system.md)             | Generic CRUD pages, custom route override               |
| [ui/filters-datatable.md](ui/filters-datatable.md) | Two-tier filter system, RSQL builder, pagination        |
| [ui/saved-searches.md](ui/saved-searches.md)       | Backend-persisted searches, BFF routes, UI behavior     |
| [ui/forms-validation.md](ui/forms-validation.md)   | Shared form component, Zod validation, field components |
| [ui/relation-picker.md](ui/relation-picker.md)     | Combobox + modal datatable picker                       |
| [ui/orchestration-ui.md](ui/orchestration-ui.md)   | Execution list, detail page, workflow widget            |
| [ui/settings-ui.md](ui/settings-ui.md)             | Settings page, complex dictionaries                     |
| [ui/navigation-abac.md](ui/navigation-abac.md)     | Sidebar, ABAC access control, route guard               |
| [ui/auth-i18n.md](ui/auth-i18n.md)                 | Keycloak-js auth, i18n setup, translation structure     |
| [ui/codegen.md](ui/codegen.md)                     | Code generation trigger, artifacts, OpenAPI extensions  |
| [ui/dependencies.md](ui/dependencies.md)           | Key packages, developer workflow, deferred features     |

---

## References

- [Temporal Java SDK — Workflow Versioning](https://docs.temporal.io/dev-guide/java/versioning)
- [Temporal Web UI — Self-Hosted Deployment](https://docs.temporal.io/web-ui)
- [Saga Pattern & Compensating Transactions](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
- [Java Annotation Processing API](https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/javax/annotation/processing/package-summary.html)
- [bpmn-js](https://bpmn.io/toolkit/bpmn-js/)
- [Как мы строили оркестрацию на Temporal (Habr)](https://habr.com/ru/articles/970730/)
- [Temporal: практика применения в продакшене (Habr)](https://habr.com/ru/articles/966972/)
