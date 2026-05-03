# Execution Model

← [Back to TDD](../tdd.md)

## 5.1 Action Enum

```java
public enum Action {
    PREVIEW,   // Dry run — no state change, no execution
    SUBMIT,    // First execution or any forward transition
    APPROVE,   // Advance to approved/active state
    REJECT,    // Operator or system rejection
    CANCEL,    // Customer-initiated or business cancellation
    CLOSE,     // Natural end of lifecycle
    ROLLBACK   // Manual compensating action
}
```

## 5.2 Signal Types

```java
public enum SignalType {
    PARTIAL,    // emitted after processing each configured batch of items
    COMPLETED   // emitted when all items are processed
}

public record Signal(
    String     source,      // mass operation code emitting this signal, or "EXTERNAL"
    SignalType type,
    Map<String, Object> payload
) {
    public static Signal external(String name) { ... }
    public static Signal from(String massOpCode, SignalType type) { ... }
}
```

## 5.3 Transition Execution — runEvent vs resumeEvent

```
ctx.runEvent(event, params?)
  ├─ Creates new workflow_execution (or new event_execution on existing instance)
  ├─ Evaluates context {} fresh
  ├─ Executes all transactions {}
  ├─ Runs finish {} and display {}
  └─ ctx.isResumed = false inside the event

ctx.resumeEvent(event, params?)
  ├─ Loads existing workflow_execution.context from PG (decrypted)
  ├─ Skips context {} evaluation
  ├─ Skips transactions {} execution
  ├─ Runs finish {} and display {} only
  └─ ctx.isResumed = true inside the event — branch on this for resume-specific logic
```

Multiple `runEvent` / `resumeEvent` calls in one transition are **async by default** (parallel `CompletablePromise`).
Use `ctx.await(...)` to synchronize.

## 5.4 MassOperation Execution Flow

```
Trigger fires (cron / signal / manual)
  │
  ├─ Load MassOperation DSL
  ├─ Evaluate lock { ctx -> } closure
  │    └─ false → abort, log LOCKED status, stop
  │
  ├─ Insert mass_operation_execution row (status: RUNNING)
  ├─ Evaluate context {} block → save to mass_operation_execution.context
  ├─ Evaluate source { ctx -> } → get item list
  ├─ Insert mass_operation_item row per item (status: PENDING)
  │    (IDs saved here serve as the business lock scope)
  │
  └─ Start Temporal MassOpWorkflow:
       │
       ├─ For each item (parallel, up to configured concurrency limit):
       │    └─ MassOpItemActivity:
       │         ├─ Update mass_operation_item status → RUNNING
       │         ├─ Evaluate item { ctx -> } closure
       │         │    └─ ctx.runWorkflow() or ctx.runEvent()
       │         ├─ On SUCCESS → mass_operation_item status: DONE
       │         └─ On FAILURE → mass_operation_item status: FAILED
       │                         log error, continue to next item
       │
       ├─ After every `partial.every` items processed:
       │    └─ Emit PARTIAL signal with payload from signals.partial { ctx -> }
       │
       ├─ After all items processed:
       │    ├─ Emit COMPLETED signal with payload from signals.completed { ctx -> }
       │    ├─ Run finish { ctx, ex -> }
       │    └─ Update mass_operation_execution status: DONE (or DONE_WITH_FAILURES)
```

## 5.5 ExecutionContext Interface Hierarchy

Context is split by lifecycle phase. Each block in the DSL receives the appropriate scoped context — preventing misuse (
e.g. calling `transactionResult()` from a `context {}` block, or `put()` from a `finish {}` block).

```
BaseContext
├── getEventCode(): String
├── getEventNumber(): Long
├── getDslVersion(): String
├── getAction(): Action
├── getWorkflowInstanceId(): Long
├── getWorkflowState(): String
├── getWorkflowCode(): String
├── resolve(Class<T>): T                 // Spring bean access
├── helper(String, Map<String, Any>): Any
├── isMassOperation: Boolean
└── isResumed: Boolean                   // true when called via resumeEvent

ParameterContext extends BaseContext
└── get(String): Any                     // read required/optional params

EnrichmentContext extends ParameterContext
└── put(String, Any): Unit               // write to context (context {} block only)

TransactionContext extends EnrichmentContext
├── transactionResult(String): Any       // read output of earlier tx
├── step(Transaction, Map?): StepHandle  // launch a step
├── await(vararg StepHandle): Unit       // sync barrier
├── condition(Condition): Boolean        // evaluate named condition
├── delegate(): Unit                     // call interface method from closure
└── println(String): Unit               // debug logging

FinishContext extends TransactionContext
├── prolong(Action): Unit                // trigger next transition internally
└── getException(): Throwable?           // null on success, populated on failure

MassOperationContext extends BaseContext
├── processedCount: Long
├── failedCount: Long
├── get("item"): Map<String, Any>        // current source row
└── emit(Signal): Unit                   // manual signal emission if needed

TransitionContext extends BaseContext
├── runEvent(event, params?): StepHandle
├── resumeEvent(event, params?): StepHandle
├── runWorkflow(code, action, eventNumber, params): StepHandle
├── await(vararg StepHandle): Unit
├── setStatus(state: String): Unit
└── states: Map<String, String>          // declared states, accessible by name
```

`finish { ctx, ex ->` receives `FinishContext` as `ctx` and `Throwable?` as `ex` (null on success).
