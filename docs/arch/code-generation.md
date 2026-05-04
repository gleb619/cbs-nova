# Code Generation Architecture

← [Back to TDD](../tdd.md)

## 13. Code Generation Architecture

### 13.1 Purpose

CBS-Nova is a **business-process orchestration engine** that lets business analysts (BAs) write rules in `.java` DSL
files and Java developers write business features (Transactions, Helpers) as plain Spring beans.  
**Code generation** is the bridge between these two worlds and Temporal's fault-tolerant execution model.

The generator produces an **intermediate layer** — a set of Temporal `Workflow` + `Activity` classes — that:

1. Wraps developer-written business logic (`@DslComponent` annotated functions).
2. Wraps BA-authored DSL orchestration rules (event sequencing, state transitions, conditions).
3. Delegates to the actual business implementation without embedding framework concerns inside it.

This follows the **Bridge / Adapter / Composite** pattern family:

| Pattern       | Role in CBS-Nova                                                                                                                                                                              |
|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bridge**    | Decouples the DSL abstraction (`EventDefinition`, `TransactionDefinition`) from the Temporal platform implementation. Both can evolve independently.                                          |
| **Adapter**   | Generated `*ActivityImpl` classes adapt the DSL type system (`TransactionInput` / `TransactionOutput`) to the developer's typed records, performing JSON ↔ POJO conversion via `JsonPayload`. |
| **Composite** | Generated `*EventWorkflow` classes compose multiple transactions, helpers, and conditions into a single Temporal workflow without hard-coding the composition in business logic.              |

---

### 13.2 Three-Layer Generation Pipeline

The pipeline runs entirely at **compile time** in the `dsl-codegen` module.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  LAYER 1 — @DslComponent Processor                                               │
│  Input:  Developer-written @DslComponent classes (*Function, *Impl)            │
│  Output: *Definition wrappers + SPI registration (ImplRegistrationProvider)      │
│  Tool:   DslComponentProcessor (Java APT)                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│  LAYER 2 — DSL Compiler                                                          │
│  Input:  BA-authored .java DSL files (Event, Workflow, MassOperation)          │
│  Output: *Definition implementations (EventDefinition, WorkflowDefinition, ...)  │
│  Tool:   DslCompiler (two-pass: registry build → import resolution)              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  LAYER 3 — Temporal Class Generator                                              │
│  Input:  Compiled *Definition classes from Layer 1 + Layer 2                     │
│  Output: Temporal Workflow interfaces + implementations                            │
│          Temporal Activity interfaces + implementations                            │
│          Generated registries (WorkflowRegistry, ActivityRegistry)                 │
│  Tool:   EventWorkflowGenerator, TransactionActivityGenerator,                     │
│          HelperActivityGenerator, WorkflowRegistryGenerator,                        │
│          ActivityRegistryGenerator                                                │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Artifact location:** All Layer-3 classes are emitted to `cbs.dsl.codegen.generated` and compiled into the
application JAR. They are never edited by hand.

---

### 13.3 Generated Class Taxonomy

For every DSL component discovered at compile time, the following classes are generated:

#### 13.3.1 Transaction → Activity Bridge

| Generated Class      | Type                    | Purpose                                                                                      |
|----------------------|-------------------------|----------------------------------------------------------------------------------------------|
| `{Code}Activity`     | `@ActivityInterface`    | Temporal contract. One `@ActivityMethod` named `execute`.                                    |
| `{Code}ActivityImpl` | Activity implementation | **Adapter:** receives generic `TransactionInput`, converts to developer's typed input via `JsonPayload`, invokes the `@DslComponent` bean, converts output back to `TransactionOutput`. |

Example mapping for a transaction coded `KYC_CHECK`:

```java
// Developer writes this — plain Java, zero Temporal imports
@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
public class KycCheckTransaction implements TransactionFunction<KycCheckInput, KycCheckOutput> {
    @Override
    public KycCheckOutput execute(KycCheckInput input) { ... }
}

// Layer 3 generates this — purely infrastructure, no business logic
@ActivityInterface
public interface KycCheckActivity {
    @ActivityMethod
    TransactionOutput execute(TransactionInput input);
}

public class KycCheckActivityImpl implements KycCheckActivity {
    private final KycCheckTransaction function;

    public KycCheckActivityImpl(DslComponentRegistry registry) {
        this.function = registry.resolve("KYC_CHECK", KycCheckTransaction.class);
    }

    @Override
    public TransactionOutput execute(TransactionInput input) {
        KycCheckInput typed = JsonPayload.fromMap(input.params(), KycCheckInput.class);
        KycCheckOutput out = function.execute(typed);
        return new TransactionOutput(JsonPayload.toMap(out));
    }
}
```

The activity is never invoked directly by application code. It is called by the **generated workflow**
inside a `TransactionsScope.step(...)` block (see §13.3.3).

---

#### 13.3.2 Helper → Activity Bridge

Helpers follow the same adapter pattern as transactions but use `HelperInput` / `HelperOutput` types.
They are typically invoked from the `context {}` block of an event, although they may also appear inside
`transactions {}` when a transaction needs pre-flight data.

| Generated Class      | Type                    | Purpose                                                           |
|----------------------|-------------------------|-------------------------------------------------------------------|
| `{Code}Activity`     | `@ActivityInterface`    | Temporal contract for helper execution.                           |
| `{Code}ActivityImpl` | Activity implementation | **Adapter:** `HelperInput` → typed helper input → `HelperOutput`. |

Consider a helper that fetches an exchange rate:

```java
// Developer writes this
@DslComponent(code = "GET_EXCHANGE_RATE", type = DslImplType.HELPER)
public class GetExchangeRateHelper implements HelperFunction<RateInput, RateOutput> {
    @Override
    public RateOutput execute(RateInput input) { ... }
}

// Generated activity
@ActivityInterface
public interface GetExchangeRateActivity {
    @ActivityMethod
    HelperOutput execute(HelperInput input);
}

public class GetExchangeRateActivityImpl implements GetExchangeRateActivity {
    private final GetExchangeRateHelper function;

    public GetExchangeRateActivityImpl(DslComponentRegistry registry) {
        this.function = registry.resolve("GET_EXCHANGE_RATE", GetExchangeRateHelper.class);
    }

    @Override
    public HelperOutput execute(HelperInput input) {
        RateInput typed = JsonPayload.fromMap(input.params(), RateInput.class);
        RateOutput out = function.execute(typed);
        return new HelperOutput(JsonPayload.toMap(out));
    }
}
```

**Helper invocation from a `context` block**

The BA writes:

```java
EventDsl.event("LOAN_DISBURSEMENT")
    .requiredParam("amount")
    .requiredParam("currency")
    .context(ctx -> {
        Object rate = ctx.helper("GET_EXCHANGE_RATE", Map.of("currency", ctx.get("currency")));
        ctx.put("rate", rate);
    })
    ...
```

The generator copies the lambda body into the generated workflow. At runtime `ctx` is backed by
`TemporalEnrichmentContext`, which routes `ctx.helper(...)` to the generated activity stub:

```java
// Inside generated LoanDisbursementEventWorkflow
private Map<String, Object> evaluateContext(EventWorkflowRequest request) {
    TemporalEnrichmentContext ctx = new TemporalEnrichmentContext(request);
    // --- copied DSL block starts ---
    HelperOutput rateResult = getExchangeRateActivity.execute(
        new HelperInput(Map.of("currency", request.params().get("currency")))
    );
    ctx.put("rate", rateResult.result().get("rate"));
    // --- copied DSL block ends ---
    return ctx.getEnrichment();
}
```

---

#### 13.3.3 Event → Workflow Bridge

Events become Temporal **Workflows** because they orchestrate multiple activities (transactions, helpers, conditions)
and must survive process restarts.

The key insight of the CBS-Nova generator is that **the DSL itself is executable Java code**.  The BA writes
`.java` files containing lambdas (`context {}`, `transactions {}`, `finish {}`).  These lambdas are
**re-used in two modes**:

| Mode                | When it runs    | `ctx` / `scope` implementation | Purpose                                                                                |
|---------------------|-----------------|--------------------------------|----------------------------------------------------------------------------------------|
| **Code-generation** | Compile time    | `CodegenEnrichmentContext`     | Records which helpers / transactions are referenced so Layer 3 knows what to generate. |
| **Runtime**         | Temporal worker | `TemporalEnrichmentContext`    | Actually calls generated activity stubs.                                               |

Because the lambda body is pure Java that only invokes methods on `ctx` or `scope`, the **same source text**
can be copied into the generated workflow class unchanged.  Only the backing implementation of the
context/scope object differs.

**BA-authored DSL**

```java
EventDsl.event("LOAN_DISBURSEMENT")
    .requiredParam("amount")
    .requiredParam("currency")
    .context(ctx -> {
        Object rate = ctx.helper("GET_EXCHANGE_RATE", Map.of("currency", ctx.get("currency")));
        ctx.put("rate", rate);
    })
    .transactions(scope -> {
        var kyc = scope.step("KYC_CHECK");
        var credit = scope.step("CREDIT_CHECK");
        scope.await(kyc, credit);
        scope.step("DISBURSE");
    })
    .finish((ctx, ex) -> { /* optional */ })
    .build();
```

**Generated workflow**

The generator emits a workflow class that **inlines the DSL blocks** and still delegates
framework concerns (persistence, state transitions, version locking) to `EventWorkflowOrchestrator`.

```java
@WorkflowInterface
public interface LoanDisbursementWorkflow {
    @WorkflowMethod(name = "LOAN_DISBURSEMENT")
    WorkflowExecutionResponse execute(EventWorkflowRequest input);
}

public class LoanDisbursementEventWorkflow implements LoanDisbursementWorkflow {

    // Framework runner — handles DB state, transitions, replay-safe timestamps
    private final EventWorkflowOrchestrator orchestrator;

    // Generated activity stubs (injected by the generated registry or looked up via Workflow.newActivityStub)
    private final KycCheckActivity kycCheckActivity;
    private final CreditCheckActivity creditCheckActivity;
    private final DisburseActivity disburseActivity;
    private final GetExchangeRateActivity getExchangeRateActivity;

    public LoanDisbursementEventWorkflow(EventWorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        this.kycCheckActivity = Workflow.newActivityStub(KycCheckActivity.class, activityOptions());
        this.creditCheckActivity = Workflow.newActivityStub(CreditCheckActivity.class, activityOptions());
        this.disburseActivity = Workflow.newActivityStub(DisburseActivity.class, activityOptions());
        this.getExchangeRateActivity = Workflow.newActivityStub(GetExchangeRateActivity.class, activityOptions());
    }

    @Override
    public WorkflowExecutionResponse execute(EventWorkflowRequest input) {
        // 1. Framework sets up workflow / event execution rows, locks version
        EventWorkflowContext ctx = orchestrator.beginEvent(input);

        // 2. Evaluate context block (copied verbatim from DSL)
        Map<String, Object> enriched = evaluateContext(input);
        ctx = ctx.withEnrichment(enriched);

        // 3. Execute transaction sequence (copied verbatim from DSL)
        WorkflowExecutionResponse response = executeTransactions(ctx);

        // 4. Framework persists final state, transition log, finish block
        return orchestrator.completeEvent(ctx, response);
    }

    // The body of this method is the exact `context {}` lambda from the DSL file.
    private Map<String, Object> evaluateContext(EventWorkflowRequest input) {
        TemporalEnrichmentContext ctx = new TemporalEnrichmentContext(input);
        // --- BEGIN copied DSL block ---
        HelperOutput rate = getExchangeRateActivity.execute(
            new HelperInput(Map.of("currency", input.params().get("currency")))
        );
        ctx.put("rate", rate.result().get("rate"));
        // --- END copied DSL block ---
        return ctx.getEnrichment();
    }

    // The body of this method is the exact `transactions {}` lambda from the DSL file.
    private WorkflowExecutionResponse executeTransactions(EventWorkflowContext ctx) {
        TemporalTransactionsScope scope = new TemporalTransactionsScope(ctx);
        // --- BEGIN copied DSL block ---
        var kyc = scope.step(() -> kycCheckActivity.execute(ctx.toTransactionInput("KYC_CHECK")));
        var credit = scope.step(() -> creditCheckActivity.execute(ctx.toTransactionInput("CREDIT_CHECK")));
        scope.await(kyc, credit);
        scope.step(() -> disburseActivity.execute(ctx.toTransactionInput("DISBURSE")));
        // --- END copied DSL block ---
        return scope.toWorkflowResponse();
    }

    private ActivityOptions activityOptions() {
        return ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .build();
    }
}
```

**Design notes**

* `TemporalTransactionsScope` implements `TransactionsScope`. Its `step(...)` overloads accept
  lambdas that invoke activity stubs and return a `StepHandle` backed by a `Promise`.  `await(...)`
  becomes `Promise.allOf(...).get()`.
* `TemporalEnrichmentContext` implements `EnrichmentContext`. Its `helper(...)` method delegates
  to the corresponding generated `*Activity` stub.
* Because the DSL block is copied **as source text**, the BA can use any local variables,
  conditionals, or helper calls — the generator does not need to parse the AST beyond identifying
  the block boundaries.
* `EventWorkflowOrchestrator` remains the single point of truth for **framework concerns**
  (DB writes, version isolation, transition logging, finish-block evaluation).  The generated
  workflow only owns the **orchestration sequence**.

---

### 13.4 Runtime Execution Flow

When an API call `POST /api/events/execute` arrives, the engine does **not** invoke business classes directly.
It follows the generated bridge:

```
HTTP POST /api/events/execute
    │
    ▼
EventController → EventService
    │
    ▼
WorkflowResolver.resolve(eventCode, dslVersion)
    │      lookup WorkflowDefinition in DslRegistry
    ▼
WorkflowExecutor.startWorkflow(eventCode, request)
    │      WorkflowClient.newUntypedWorkflowStub("LOAN_DISBURSEMENT", options)
    ▼
Temporal Server schedules the workflow worker
    │
    ▼
Generated LoanDisbursementEventWorkflow.execute(request)
    │      ├─ evaluateContext()  → calls Helper activity stubs (e.g. GET_EXCHANGE_RATE)
    │      ├─ beginEvent() via EventWorkflowOrchestrator (persistence, state machine)
    │      └─ executeTransactions() → calls Transaction activity stubs via TransactionsScope
    ▼
Generated *ActivityImpl.execute(input)
    │      JSON → typed POJO adaptation
    ▼
Developer-written @DslComponent class.execute(typedInput)
    │      pure business logic
    ▼
Result bubbles back up through the same layers,
persisting state in PostgreSQL at each checkpoint.
```

---

### 13.5 Runner Pattern in `starter` Services

The `starter` module contains **orchestrator/runner** services that sit between the generated Temporal classes
and the persistence layer.  These classes are **not** generated; they are hand-written framework code that uses the
DSL registry to remain generic across all business entities.

```
starter/src/main/java/cbs/nova/service/
├── EventWorkflowOrchestrator.java        ← runner for single-event workflows
├── MassOperationScheduler.java           ← runner for mass-operation workflows
├── WorkflowExecutor.java                 ← abstracts WorkflowClient.start()
├── WorkflowResolver.java                 ← resolves code + version → definition
└── ContextEvaluator.java                 ← evaluates context {} blocks
```

A future `{BusinessEntity}Runner` (e.g. `LoanDisbursementRunner`) would follow this contract:

1. **Inject** the generated-or-generic `EventWorkflowOrchestrator` (or a more specific orchestrator).
2. **Resolve** the DSL definition for the entity from `DslRegistry`.
3. **Coordinate** state transitions, compensation, and persistence.
4. **Delegate** actual business work to the generated activity stubs.

The key design principle: **Runners know about DSL definitions and Temporal APIs; they never know about the
internal structure of a Transaction or Helper.**  That knowledge lives only in:

- `@DslComponent` developer code (business logic).
- Generated `*ActivityImpl` adapters (type conversion).
- Generated `*EventWorkflow` classes (orchestration sequence copied from DSL).

---

### 13.6 Registry Wiring at Startup

At application startup, the framework wires everything together without reflection-based classpath scanning:

```java
// GeneratedWorkflowRegistry.registerAll(worker, orchestrator);
// GeneratedActivityRegistry.registerAll(worker);
```

These generated registry classes enumerate every discovered workflow/activity class as explicit `register...`
calls.  This is both faster than scanning and deterministic — the set of registered workflows exactly matches
the set of `@DslComponent` + DSL files present at compile time.

For development mode (`@Profile("dev")`), the registry is populated from raw `.java` DSL files via
`ReflectiveWorkflow` / `ReflectiveActivity` wrappers instead of generated classes.  The runtime contract
(`DslRegistry` lookups) remains identical.

---

### 13.7 Version Isolation and Code Generation

Because generated classes are compiled into the JAR alongside the DSL version they were built from, the engine
can enforce **strict version isolation** (see [versioning.md](versioning.md)):

- A workflow instance is locked to the `dslVersion` it started with.
- The generated `Workflow` / `Activity` class for that version is the only code Temporal ever sees for that
  instance.
- Old workers drain in-flight workflows; new workers serve new starts.

This means the generated bridge layer is **immutable for a given deployment** — there is no runtime reloading
of generated classes.

---

### 13.8 Summary: Who Writes What

| Layer              | Author                | Artifact                                                 | Temporal Awareness                                    |
|--------------------|-----------------------|----------------------------------------------------------|-------------------------------------------------------|
| Business Function  | Java Developer        | `@DslComponent` class                                    | **None**                                              |
| DSL Orchestration  | Business Analyst      | `.java` DSL file                                         | **None**                                              |
| Definition Wrapper | Generator (Layer 1/2) | `*Definition` interface impl                             | **None**                                              |
| Temporal Bridge    | Generator (Layer 3)   | `*Workflow`, `*Activity`                                 | **Full** — `@WorkflowInterface`, `@ActivityInterface` |
| Execution Runner   | Framework Developer   | `EventWorkflowOrchestrator`, `*Runner`                   | **Full** — `WorkflowClient`, `ActivityStub`           |
| Registry Wiring    | Generator (Layer 3)   | `GeneratedWorkflowRegistry`, `GeneratedActivityRegistry` | **Full** — `Worker.register...`                       |

The generated code is the **glue** that lets business authors and developers remain completely ignorant of
Temporal, while the engine gains retries, queues, sagas, and durable execution for free.

---

## References

- [DSL Design](dsl-design.md) — Layer 1 & 2 details, `@DslComponent`, two-pass compilation, dual-mode DSL contexts
- [Module Structure](module-structure.md) — `dsl-codegen` file tree, dependency graph
- [Execution Model](execution-model.md) — `runEvent` vs `resumeEvent`, context hierarchy
- [Versioning](versioning.md) — strict isolation, workflow ID format, worker drain
- [Workflow Lifecycle](workflow-lifecycle.md) — state machine, `prolong()`, transitions
