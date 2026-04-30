# CBS Nova — Product Overview

CBS Nova is a **business process orchestration engine** for core banking operations. It replaces Spring-bean
orchestration with a Temporal + PostgreSQL backend and a Java DSL for business rules. Non-developers author rules in
`.java` DSL files; the engine generates Temporal workflows/activities from them (production) or executes them via
reflection (development).

## Core Concepts

| Entity            | Role                                                                                                            |
|-------------------|-----------------------------------------------------------------------------------------------------------------|
| **Workflow**      | State machine backed by Temporal. All executions (even stateless) use a Temporal workflow.                      |
| **Event**         | Triggered operation inside a workflow state. Has `context{}`, `display{}`, `transactions{}`, `finish{}` blocks. |
| **Transaction**   | Unit of work with optional `preview()` / `execute()` / `rollback()`. Rollback is a compensating entry.          |
| **Helper**        | Spring bean or inline DSL (SQL/HTTP). Typed as `HelperFunction<I, O>`.                                          |
| **MassOperation** | Batch orchestration of the same Event/Workflow over a dataset.                                                  |
| **Condition**     | Boolean DSL block, reusable across events.                                                                      |

## Execution Flow

```
POST /api/events/execute
  → Spring: evaluate context{} (fails fast before Temporal)
  → Temporal workflow: state transition
  → Transaction chain: preview → execute (→ rollback on failure)
  → PostgreSQL: persist context, display_data, executed_transactions (JSONB, encrypted)
```

- Single API endpoint `/api/events/execute` — callers use `eventNumber`, not `workflowInstanceId`
- Temporal holds **only PKs**; all state lives in PostgreSQL
- Context accumulates across transitions; `ctx.prolong()` auto-advances to next state

## Services

| Service               | Port    | Description                                        |
|-----------------------|---------|----------------------------------------------------|
| Backend (Spring Boot) | `:7070` | Java API server + Temporal workflows               |
| Nuxt.js BFF           | `:3000` | Backend-for-Frontend, proxies `/api/**` to backend |
| Vite Admin UI         | `:9000` | Vue 3 SPA, proxies `/api/**` to Nuxt BFF           |

Request flow: `Browser → Vite (9000) → Nuxt BFF (3000) → Backend (7070)`

## Status

Project is in **design/early implementation phase**. `docs/` contains Technical Design Documents.
