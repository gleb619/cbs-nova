# Workflow Lifecycle Model

← [Back to TDD](../tdd.md)

## 6.1 State Machine Concepts

| Concept          | Description                                                                    |
|------------------|--------------------------------------------------------------------------------|
| `states`         | Optional. Inferred from transitions if omitted.                                |
| `initial`        | Optional. Defaults to first `from` state in transitions.                       |
| `terminalStates` | Optional. Defaults to states with no outgoing transitions.                     |
| `FAULTED`        | Framework-reserved. Set on auto-rollback after failure.                        |
| `onFault`        | Per-transition. Target state on failure. Defaults to `"FAULTED"`.              |
| Stub workflow    | Auto-generated for events with no explicit workflow DSL. One state: COMPLETED. |
| Context sharing  | `workflow_execution.context` is shared across all transitions. No fresh seed.  |

## 6.2 Example Lifecycle: Loan Contract

```
  [PREVIEW — no persisted state]
        │
        │ SUBMIT
        ▼
    ENTERED ◄──────────────────────────── FAULTED
        │                                    ▲
        │ APPROVE (or ctx.prolong(APPROVE))  │ (auto on failure)
        ▼                                    │
    ACTIVE ──────────────────────────────────┘
        │
        ├── CANCEL ──► CANCELLED (terminal)
        └── CLOSE  ──► CLOSED   (terminal)

    ENTERED ── CANCEL  ──► CANCELLED (terminal)
    ENTERED ── REJECT  ──► ENTERED   (stays, notice sent)
    FAULTED ── ROLLBACK ──► ENTERED  (manual recovery)
```

## 6.3 Auto-Advance via ctx.prolong()

When `ctx.prolong(action)` is called inside `finish {}`, the framework triggers the next workflow transition in a
separate thread after a short delay — without a network round-trip or external API call. This is the integration point
for legacy system behavior where certain state progressions were automatic.

There is no maximum chain depth enforced by the framework. DSL authors are responsible for avoiding infinite prolong
loops. Terminal states are always checked before triggering — `prolong()` on a terminal state is a no-op.
