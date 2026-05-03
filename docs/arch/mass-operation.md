# Mass Operation Model

← [Back to TDD](../tdd.md)

## 7.1 Triggers

| Trigger type   | DSL                                                  | Description                     |
|----------------|------------------------------------------------------|---------------------------------|
| Cron           | `cron("0 1 * * *")`                                  | Standard cron expression        |
| Exact time     | `once(at = "2025-12-31T23:59:00")`                   | Run once at specific datetime   |
| Periodic       | `every(days = 1)`, `every(weeks = 1)`                | Repeating interval              |
| External       | `onSignal(Signal.external("NAME"))`                  | MQ message or webhook           |
| Signal from op | `onSignal(Signal.from("OP_CODE", Signal.COMPLETED))` | Triggered by another mass op    |
| Signal partial | `onSignal(Signal.from("OP_CODE", Signal.PARTIAL))`   | Triggered on partial completion |

Multiple triggers can be declared — any one firing starts the operation (subject to lock).

## 7.2 Business Lock

The lock closure returns a `Boolean`. `true` = allowed to start. `false` = locked, operation aborted and logged.

The `mass_operation_item` table is populated at the start of each run with the IDs of all items to be processed. This
serves as the definitive scope of the run — later re-runs of failed items reference the same `mass_operation_execution`
row and its item list. Re-running the entire mass operation is forbidden by the framework (enforced by the lock
closure + status check).

## 7.3 Signals

```
PARTIAL signal  → emitted every N items (configured per operation)
COMPLETED signal → emitted when all items processed

Signal payload  → declared in signals {} closure, available to receivers
Signal receiver → declared in triggers {} of another mass operation
               → or consumed by external systems via MQ/webhook subscription
```

## 7.4 Categories

Category is a free-form string declared in DSL. Used for:
- Admin UI grouping and filtering
- Access control (chief product owners see only their category)
- Reporting aggregation

Standard categories (not hardcoded — declared per operation):
`CREDITS`, `DEPOSITS`, `COLLATERALS`, `COLLECTIONS`, `REPORTING`

## 7.5 Failed Item Re-run

Failed items (`mass_operation_item.status = FAILED`) can be re-run individually from the admin UI:

```
POST /api/mass-operations/{executionId}/items/{itemId}/retry
```

This creates a new `mass_operation_item` row linked to the same `mass_operation_execution`, re-evaluates the
`item { ctx -> }` closure for that item only, and updates the item status. The parent operation's `DONE_WITH_FAILURES`
status does not change — but `failed_count` is decremented on successful retry.

Re-running the entire mass operation is forbidden. A v2 feature will allow computing unprocessed items on-the-fly and
creating a new operation for them.
