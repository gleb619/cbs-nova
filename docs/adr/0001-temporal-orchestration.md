# 0001. Use Temporal as the orchestration engine

- **Status:** Accepted
- **Date:** 2026-09-02 (retroactive — records a foundational decision)

## Context

cbs-nova exists to run long-lived, multi-step business flows (a "Process" is a sequence of
"Transactions") that must survive process restarts, retry failed steps with backoff, compensate
partial work (sagas), and remain inspectable while in flight. The authoring surface is a
declarative Java DSL; whatever runs the flows has to provide durable execution so the DSL author
never writes checkpointing, timer, or retry plumbing by hand.

Options considered:

- **Temporal** — durable execution engine; workflows are replayed from an event history, activities
  are retried independently, timers and signals are first-class. Requires running a Temporal server
  (plus its own datastore) and accepting the workflow determinism constraints.
- **Spring State Machine** — in-process, no extra infrastructure, but no durability: a JVM restart
  loses in-flight state unless we build persistence, recovery, and retry ourselves. That "ourselves"
  is most of what Temporal already is.
- **Camunda / BPMN engine** — durable and mature, but XML/BPMN-centric; the modelling surface fights
  a code-first Java DSL, and the runtime is heavier than needed.
- **Custom orchestrator** on a job queue + database — full control, but re-implements durable
  timers, exactly-once activity semantics, history, and visibility. High ongoing cost, low
  differentiation.

## Decision

We will use **Temporal** as the execution engine. The DSL compiler generates Temporal Workflow and
Activity classes at build time (`Process` → Workflow, `Transaction` → Activity); the generated code
talks to a single runtime facade (`GlobalManager.getInstance()`) rather than to Temporal APIs
directly, so the Temporal coupling lives in generated code and the runtime layer, not in
hand-written business logic.

To keep local development and tests fast, the engine also supports a **Preview (dry-run)** mode that
executes DSL definitions directly through `GlobalManager` without a Temporal cluster. Preview is not
durable and is not a substitute for Run mode; it exists for validation and the Explain diagram.

## Consequences

**Positive**

- Durable timers, per-activity retry, saga compensation, and in-flight visibility come from the
  engine; the DSL author and the runtime layer do not implement them.
- The `Process`/`Transaction` split maps cleanly onto Workflow/Activity, which keeps the DSL's
  execution contract honest.
- Preview mode gives a no-infrastructure feedback loop despite the heavyweight production engine.

**Negative**

- Operations must run and monitor a Temporal server and its datastore (`app/compose/` provides the
  local slice; see `architecture-backend.md`).
- Workflow determinism constrains generated code: non-deterministic calls must go through activities.
  The codegen layer owns this rule.
- Two execution paths (Temporal for Run, direct for Preview/Explain) must be kept behaviourally
  aligned; divergence between them is a recurring class of bug.

**Neutral**

- Temporal's Java SDK version and server version become dependencies to track alongside the JDK.
