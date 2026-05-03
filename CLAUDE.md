# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

CBS-Nova is a **business process orchestration engine** for core banking. It replaces Spring-bean orchestration with a
Temporal + PostgreSQL backend and a Kotlin Script DSL for business rules. Non-developers author rules in `.kts` files;
the engine compiles and executes them.

**Stack:** Java 25 · Spring Boot · Temporal · PostgreSQL · Kotlin Script (.kts) · Vue 3 admin UI · Nuxt.js BFF · Biome (linter/formatter) · Gradle multi-module build

> This repo is currently in design phase. `docs/` contains the Technical Design Documents; no implementation code exists
> yet.

---

## Architecture

### Core Entities

| Entity            | Role                                                                                                                                                        |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Workflow**      | State machine. All executions (even stateless) are backed by a Temporal workflow. Stateless events use a stub wrapper — sugar only, not a code distinction. |
| **Event**         | Triggered operation; lives inside a workflow state. Has `context{}`, `display{}`, `transactions{}`, `finish{}` blocks.                                      |
| **Transaction**   | Unit of work with optional `preview()` / `execute()` / `rollback()`. Rollback is a compensating entry (real reversal), not a technical undo.                |
| **Helper**        | Spring bean or inline DSL (SQL/HTTP). Typed as `HelperFunction<I, O>`.                                                                                      |
| **MassOperation** | Batch orchestration of the same Event/Workflow over a dataset. Has triggers, per-item tracking, and a signal system for chaining operations.                |
| **Condition**     | Boolean DSL block, reusable across events.                                                                                                                  |

### Execution Flow

```
POST /api/events/execute
  → Spring: evaluate context{} (fails fast, no Temporal invoked on failure)
  → Temporal workflow: state transition
  → Transaction chain: preview → execute (→ rollback on failure)
  → PostgreSQL: persist context, display_data, executed_transactions (JSONB, encrypted)
```

- Temporal holds **only PKs**; all state lives in PostgreSQL.
- Context **accumulates across transitions** — no fresh seed between states.
- `ctx.prolong()` auto-advances to the next state.
- Single API endpoint `/api/events/execute` hides the workflow/event distinction from callers. Callers use `eventNumber`, not `workflowInstanceId`.

### DSL

Files are Kotlin Script (`.kts`), stored in the `cbs-rules` Gitea repo. They compile to a JAR via Gradle; dev-time evaluation uses JSR-223.

```
cbs-rules/
  loan-contract/          ← workflow at folder root
    workflow.kts
    disbursement/         ← event in subfolder
      event.kts
      helpers.kts
```

Actions: `PREVIEW · SUBMIT · APPROVE · REJECT · CANCEL · CLOSE · ROLLBACK`

### Versioning

Strict isolation — instances are locked to the DSL version they started with. No cross-version interop. Format:
`{semver}-{gitCommitShort}` embedded in the compiled JAR manifest.

### MassOperation Signals

MassOperations emit `PARTIAL` (every N items) and `COMPLETED` signals with custom payloads. Other operations subscribe
and auto-trigger — this is the primary chaining mechanism for batch pipelines.

### Module Structure (Gradle multi-module)

```
backend/          Spring Boot entry point (Java 25)
frontend/         Vue 3 admin UI (Vite, port 9000) + Nuxt.js BFF (port 3000)
├─ src/           Vue SPA source
├─ nuxt/          Nuxt.js BFF application
├─ biome.json     Biome linter/formatter config (replaced ESLint+Prettier)
└─ build.gradle.kts  pnpm-based build tasks
```

### Build Pipeline

Gitea (`cbs-rules`) → GitLab/Jenkins → Gradle compile → Docker

---

## Key Design Decisions

- **No nullable `workflow_execution_id`** — every execution has a backing workflow instance.
- **Kotlin Script over Groovy/ANTLR** — type safety, IDE support, compile-time validation.
- **Pre-Temporal context evaluation** — Spring validates context blocks before touching Temporal; avoids orphaned
  workflow instances on bad input.
- **`display{}` is optional** — defaults to context values if omitted; can also appear inside individual transactions.
- **`finish{ctx, ex}`** — runs on both success and failure; `ex` is null on success. Used for notifications and
  `prolong()` chaining.
