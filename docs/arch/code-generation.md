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

---


### 13.1a Temporal Integration Scope

Code generation for Temporal is limited to three DSL component types:

| DSL Component     | Generated Temporal Artifact           | Notes                                                                               |
|-------------------|---------------------------------------|-------------------------------------------------------------------------------------|
| **Event**         | `EventWorkflow` + `EventActivity`     | Event is the only top-level Temporal Workflow. All execution starts as an Event.    |
| **Transaction**   | `TransactionActivity` (Activity only) | No workflow generated. Called from EventWorkflow.                                   |
| **Condition**     | `ConditionActivity` (Activity only)   | No workflow generated. Used inside EventWorkflow for branching decisions.           |
| **Helper**        | **None** (plain Spring bean)          | Helpers are invoked synchronously inside TransactionActivity. No Temporal artifact. |
| **Workflow**      | **None** (state machine DSL only)     | Workflow definitions orchestrate events but do not generate Temporal workflows.     |
| **MassOperation** | **None** (out of scope)               | Batch orchestration handled separately.                                             |

**Key rules:**
- Events can be created **only via DSL** (not via `@DslComponent` code classes).
- Transactions, Helpers, and Conditions can be created via **code** (`@DslComponent`) or **DSL**.
- The generated `EventWorkflow` first calls `EventActivity.prepareContext()` to execute the event's `context{}` block, then iterates through the event's transaction codes, calling each `TransactionActivity`.
- `TransactionActivity` internally calls helper `prepareContext()` / `execute()` as plain synchronous code before running the developer's business logic.

> **Terminology note:** Every generated artifact must have a `*Definition` wrapper, even for non-Temporal components. For Temporal workflows the registry layer uses the word **Specification** (produced by `SpecificationGenerator` as `GeneratedSpecificationRegistry`).

### 13.2 Three-Layer Generation Pipeline

The pipeline runs entirely at **compile time** in the `dsl-codegen` module.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  LAYER 1 — @DslComponent Processor                                               │
│  Input:  Developer-written @DslComponent classes (Transaction, Helper, Condition)│
│  Output: *Definition wrappers + SPI registration (ImplRegistrationProvider)      │
│  Tool:   DslComponentProcessor (Java APT)                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│  LAYER 2 — DSL Compiler                                                          │
│  Input:  BA-authored .java DSL files (Event, Workflow, MassOperation, and inline │
│          Transaction, Helper, Condition definitions)                              │
│  Output: *Definition implementations (EventDefinition, TransactionDefinition, ...) │
│  Tool:   DslCompiler (two-pass: registry build → import resolution)              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  LAYER 3 — Temporal Class Generator                                              │
│  Input:  Compiled *Definition classes from Layer 1 + Layer 2                     │
│  Output: Temporal Workflow  : EventWorkflow (Event only)                         │
│          Temporal Activities : EventActivity, TransactionActivity, ConditionActivity│
│          Executable* contracts (ExecutableEvent, ExecutableTransaction, …)            │
│          Generated registries (EventRegistry, TransactionRegistry, ConditionRegistry)│
│  Tool:   EventCodeGenerator, TransactionCodeGenerator, ConditionCodeGenerator,      │
│          SpecificationGenerator                       │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Artifact location:** All Layer-3 classes are emitted to `cbs.dsl.codegen.generated` and compiled into the
application JAR. They are never edited by hand.

---

### 13.3 Generated Class Taxonomy

For every DSL component discovered at compile time, the following classes are generated:

#### 13.3.1 Transaction → Activity Bridge

| Generated Class               | Type                       | Purpose                                                                                                                                                                                                                                                                          |
|-------------------------------|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{Code}Activity`              | `@ActivityInterface`       | Temporal contract. One `@ActivityMethod` named `execute`.                                                                                                                                                                                                                        |
| `{Code}TransactionDefinition` | Definition + Activity impl | **Adapter:** implements both `TransactionDefinition` and `{Code}Activity`. Receives generic `TransactionInput`, converts to developer's typed input via `JsonPayload`, invokes the `@DslComponent` bean, converts output back to `TransactionOutput`. Hosts the DSL via `dsl()`. |

Example mapping for a transaction coded `KYC_CHECK`:

```java
// Developer writes this — plain Java, zero Temporal imports
@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
public class KycCheckTransaction implements TransactionFunction<KycCheckInput, KycCheckOutput> {
    @Override
    public TransactionContext<KycCheckOutput> execute(TransactionContext<KycCheckInput> ctx) { ... }
}

// Layer 3 generates this — purely infrastructure, no business logic
@ActivityInterface
public interface KycCheckActivity {
    @ActivityMethod
    TransactionContext<TransactionOutput> execute(TransactionContext<TransactionInput> ctx);
}

public class KycCheckTransactionDefinition implements TransactionDefinition, KycCheckActivity {

    private final KycCheckTransaction function;

    public KycCheckTransactionDefinition(DslComponentResolver resolver) {
        //TODO: old way
        //this.function = resolver != null ? resolver.resolve(KycCheckTransaction.class) : new KycCheckTransaction();
        //TODO: new way
        this.function = resolver.resolve(KycCheckTransaction.class);
    }

    @Override
    public String getCode() { return "KYC_CHECK"; }

    @Override
    public TransactionContext<TransactionOutput> execute(TransactionContext<TransactionInput> input) {
        TransactionInput input = ctx.payload();
        KycCheckInput typed = JsonPayload.fromMap(input.params(), KycCheckInput.class);
        KycCheckOutput out = function.execute(typed);

        //TODO: we need to merge here dsl/transactionEvaluator/KycCheckTransaction call

        return new TransactionOutput(JsonPayload.toMap(out));
    }

    @Override
    public DslObject dsl() {
        //TODO: Use `ParsedDsl` from `DslCompiler` and set whole dsl here
    }
}
```

//TODO: dsl for transacitons used for override/overload features, but sometimes we want create dsl for transaction but still call it from an event dsl. For this cases we need to create another codeGenerator to create Definition for transactions without dsl. They worked as is, a direct call.

The activity is never invoked directly by application code. It is called by the **generated workflow**
inside a `TransactionsScope.step(...)` block (see §13.3.3). The `dsl()` method exposes the original DSL
blocks so they can be evaluated lazily at runtime (e.g. `transactionEvaluator.evaluate(dsl().context(), ctx)`).

---

#### 13.3.2 Helper — Plain Logic (No Temporal Bridge)

Helpers are **not Temporal activities**. They remain plain Java/DSL logic that is executed
synchronously inside the calling Transaction or Condition activity (or inside the Event activity
for `context {}` blocks). There is **no generated `HelperActivity`** and no Temporal stub for helpers.

When a BA writes:

```java
EventDsl.event("LOAN_DISBURSEMENT")
    .context(ctx -> {
        Object rate = ctx.helper("GET_EXCHANGE_RATE", Map.of("currency", ctx.get("currency")));
        ctx.put("rate", rate);
    })
```

The generator pastes this block into the generated `EventWorkflow`. At runtime `ctx.helper(...)`
resolves the helper via the `HelperRegistry` and invokes it directly (synchronous, in-memory).

Similarly, when a Transaction developer calls a helper inside their `@DslComponent` execute method,
it is a plain Java method call — no Temporal activity stub is involved.

---

#### 13.3.3 Condition → Activity Bridge

Conditions are Temporal **Activities** because they may need to call external services or helpers
to evaluate a boolean predicate, and they must be replay-safe.

| Generated Class              | Type                       | Purpose                                                                                                                            |
|------------------------------|----------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `{Code}Activity`             | `@ActivityInterface`       | Temporal contract. One `@ActivityMethod` named `evaluate`.                                                                         |
| `{Code}ConditionDefinition`  | Definition + Activity impl | **Adapter:** implements both `ConditionDefinition` and `{Code}Activity`. `ConditionInput` → typed input → boolean result.          |

The generated activity is invoked from the Event workflow inside `when/then/otherwise` branches.

---

#### 13.3.4 Event → Workflow Bridge

Events are **DSL-only** and become Temporal **Workflows** because they orchestrate multiple
Temporal activities (`TransactionActivity`, `ConditionActivity`). The workflow must survive
process restarts — if the app crashes, Temporal restores state and continues execution.

The generated code for an Event produces **two classes**:
* **`{Code}EventWorkflow`** — `@WorkflowInterface` that drives the orchestration.
* **`{Code}EventActivity`** — `@ActivityInterface` that evaluates the `context {}` block.

**Execution flow inside the generated workflow**

1. **Prepare context** — the workflow calls `eventActivity.prepareContext(inputParams)`.  
   This evaluates the DSL `context {}` block (enrichment, helper calls, etc.) and returns a
   prefilled context object.

2. **Run transactions** — the DSL `transactions {}` block is **pasted as source text** into the
   generated workflow. It runs inside the workflow thread and decides which steps to invoke:
   * For each `scope.step("TX_CODE")` the workflow calls:
     * `transactionActivity.prepareContext(currentCtx)`
     * `transactionActivity.execute(preparedCtx)`
   * For each `when/then/otherwise` branch the workflow calls:
     * `conditionActivity.evaluate(currentCtx)`

3. **Finish** — optional `finish {}` block is also pasted into the workflow.

Helpers are **not Temporal activities** — they are resolved via `HelperRegistry` and invoked
synchronously (plain Java method calls) inside the Event activity or Transaction activity.

**BA-authored DSL**

```java
EventDsl.event("LOAN_DISBURSEMENT")
    .parameters(reg -> {
        reg.decimal("amount");
        reg.string("currency");
    })
    .context(ctx -> {
        Object rate = ctx.helper("GET_EXCHANGE_RATE", Map.of("currency", ctx.get("currency")));
        ctx.put("rate", rate);
    })
    .transactions(scope -> {
        var kyc = scope.step("KYC_CHECK");
        var credit = scope.step("CREDIT_CHECK");
        scope.await(kyc, credit);
        var debit = scope.stepWhen(ctx.condition("BORROWER_ACCOUNT_READY"))
            .then("DEBIT_FUNDING_ACCOUNT")
            .otherwise("DEBIT_FALLBACK_ACCOUNT");
        scope.await(debit);
    })
    .finish((ctx, ex) -> { /* optional */ })
    .build();
```

**Generated workflow (simplified)**

```java
@WorkflowInterface
public interface LoanDisbursementWorkflow {
    @WorkflowMethod
    EventResult execute(EventInput request);
}

public class LoanDisbursementEventWorkflow implements LoanDisbursementWorkflow {

    private final LoanDisbursementEventActivity eventActivity;
    private final KycCheckTransactionActivity kycActivity;
    private final CreditCheckTransactionActivity creditActivity;
    private final DebitFundingAccountTransactionActivity debitActivity;
    private final BorrowerAccountReadyConditionActivity conditionActivity;

    @Override
    public EventResult execute(EventInput request) {
        // 1. Prepare context via EventActivity
        EventContext ctx = eventActivity.prepareContext(request);

        // 2. Evaluate transactions block (pasted from DSL)
        var kyc = kycActivity.execute(kycActivity.prepareContext(ctx));
        var credit = creditActivity.execute(creditActivity.prepareContext(ctx));
        Workflow.await(() -> kyc.isDone() && credit.isDone());

        boolean ready = conditionActivity.evaluate(ctx);
        if (ready) {
            debitActivity.execute(debitActivity.prepareContext(ctx));
        }

        return new EventResult(ctx);
    }
}
```

**Design notes**

* The DSL `transactions {}` block is copied **as source text** into the generated workflow.
  The BA can use any local variables, conditionals, or helper calls — the generator does not need
  to parse the AST beyond identifying the block boundaries.
* `ctx.helper(...)` inside the workflow resolves the helper via `HelperRegistry` and calls it
  synchronously (no Temporal stub, no activity retry).
* Transaction activities receive the current context, call `prepareContext()` internally if needed,
  then execute business logic.
* The generated workflow is **persistence-agnostic**.  DB writes, version isolation, and transition
  logging are handled by `EventService` when it calls `EventRunner` (see §13.5).

---

#### 13.3.5 Layer-1 Definition Wrappers and `componentModel`

Layer 1 generates `*Definition` wrappers that implement the `dsl-api` contract interfaces
(`TransactionDefinition`, `HelperDefinition`, `EventDefinition`, `WorkflowDefinition`,
`ConditionDefinition`, `MassOperationDefinition`).  These wrappers are **not** Temporal-aware
by themselves, but for transactions, and events they also implement the Temporal
activity/workflow interface so that the definition class **is** the bridge implementation.

All generated wrappers expose the original DSL blocks via `dsl()`, returning `DslObject`.
For generated definitions this is `this`; for builder-created definitions it is the concrete
`*DslObject` that holds the original DSL lambdas for lazy evaluation at runtime.

**Generated wrapper structure**

For a component `KycCheckTransaction`:

```java
public class KycCheckTransactionDefinition implements TransactionDefinition, KycCheckActivity {

    private final KycCheckTransaction function;

    public KycCheckTransactionDefinition() {
        this(null);
    }

    public KycCheckTransactionDefinition(DslComponentResolver resolver) {
        this.function = resolver != null
            ? resolver.resolve(KycCheckTransaction.class)
            : new KycCheckTransaction();
    }

    @Override
    public TransactionContext<TransactionOutput> execute(TransactionContext<TransactionInput> ctx) {
        TransactionInput input = ctx.payload()
        KycCheckInput typed = JsonPayload.fromMap(input.params(), KycCheckInput.class);
        KycCheckOutput out = function.execute(typed);
        return new TransactionOutput(JsonPayload.toMap(out));
    }

    @Override
    public DslObject dsl() {
      //TODO: Use `ParsedDsl` from `DslCompiler` and set whole dsl here
    }

    // ... preview(), rollback(), getCode()
}
```

**Component model**

The `@DslComponent` annotation has a `componentModel()` attribute that controls how the wrapper
obtains the component instance at runtime:

| Model            | Compile-time behaviour                                                                                                       | Runtime behaviour                                                               |
|------------------|------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `SIMPLE`         | Annotation processor records `SIMPLE`.                                                                                       | Wrapper calls `new KycCheckTransaction()`.                                      |
| `SPRING`         | Annotation processor records `SPRING`.                                                                                       | Wrapper delegates to `DslComponentResolver.resolve(KycCheckTransaction.class)`. |
| `AUTO` (default) | Processor inspects the class for any `org.springframework.*` annotation. If found, resolves to `SPRING`; otherwise `SIMPLE`. | Same as the resolved model.                                                     |

`AUTO` means the developer does **not** need to think about `componentModel` for the common
case: a plain POJO gets `SIMPLE`, a `@Service` or `@Component` gets `SPRING`.

**`DslComponentResolver`**

```java
public interface DslComponentResolver {
    <T> T resolve(Class<T> type);
}
```

The framework provides `SpringDslComponentResolver` in the `starter` module — a `@Component`
that delegates to `ApplicationContext.getBean(Class)`.  Outside a Spring context the resolver is
`null` and the wrapper falls back to plain construction, so tests and non-Spring environments
continue to work without any configuration.

**Registration with resolver**

`ImplRegistrationProvider` exposes a two-argument `register(registry, resolver)` method.
`SpiImplRegistryLoader.loadInto(registry, resolver)` iterates over every SPI-discovered
provider and passes the resolver.  When `ImplRegistryAutoConfiguration` creates the
`DslRegistry` bean it injects the `SpringDslComponentResolver` and calls
`SpiImplRegistryLoader.loadInto(registry, resolver)`, so every generated wrapper that needs a
Spring-managed bean receives the resolver.

---

### 13.4 Runtime Execution Flow

When an API call `POST /api/events/execute` arrives with an event `code` and a `params` HashMap,
the engine registers the execution in the database and then delegates to the runner layer.

```
HTTP POST /api/events/execute  { code: "LOAN_DISBURSEMENT", params: { ... } }
    │
    ▼
EventController
    │
    ▼
EventService.execute(eventCode, params)
    │      1. Register execution in DB (id, code, performer, date, input params, status)
    │      2. Build EventContext from params
    │      3. Delegate to EventRunner
    ▼
EventRunner.run(eventCode, ctx)
    │      lookup code-generated EventWorkflow in EventRegistry by code
    ▼
ExecutableEvent.execute(ctx)
    │      ├─ GENERATED mode: start Temporal EventWorkflow
    │      └─ REFLECTED mode: direct in-memory execution
    ▼
Generated EventWorkflow
    │      1. Calls EventActivity.prepareContext(params) → enriched context
    │      2. Evaluates transactions block (pasted DSL code)
    │         ├─ For each transaction: TransactionActivity.prepareContext(ctx)
    │         │                              TransactionActivity.execute(ctx)
    │         └─ For conditions: ConditionActivity.evaluate(ctx)
    ▼
TransactionActivity.execute(ctx)
    │      1. Calls Helper.prepareContext() / Helper.execute() synchronously (plain code)
    │      2. Runs developer business logic
    ▼
Result bubbles back up through the same layers.
EventService persists the final result, logs, and transition state.
```

---

### 13.5 Runner / Service Pattern in `starter`

The `starter` module provides **Runner** beans that execute DSL components by resolving them
from compile-time generated registries.  Runners are thin, stateless, and persistence-agnostic.
They are the primary execution API for both tests and services.

```
starter/src/main/java/cbs/nova/runner/
├── EventRunner.java          ← resolves ExecutableEvent from EventRegistry and runs it
├── TransactionRunner.java    ← resolves ExecutableTransaction from TransactionRegistry
├── ConditionRunner.java      ← resolves ExecutableCondition from ConditionRegistry
├── HelperRunner.java         ← resolves ExecutableHelper from HelperRegistry (preview/reflective only)
├── WorkflowRunner.java       ← resolves ExecutableWorkflow from WorkflowRegistry (preview/reflective only)
└── MassOpRunner.java         ← resolves ExecutableMassOp from MassOpRegistry (preview/reflective only)

starter/src/main/java/cbs/nova/service/
├── EventService.java         ← EventRunner + DB persistence (workflow_execution, event_execution, transition_log)
├── TransactionService.java   ← TransactionRunner + execution artifact persistence
├── ConditionService.java     ← ConditionRunner + evaluation logging
├── HelperService.java        ← HelperRunner + invocation logging
├── WorkflowService.java      ← WorkflowRunner + DB persistence
└── MassOperationService.java ← MassOpRunner + mass_operation_execution / _item persistence
```

#### Runners

A runner follows a uniform contract: resolve an `Executable*` implementation by its string code
from the generated registry, then invoke it.

```java
@Component
public class TransactionRunner {
    private final TransactionRegistry registry;

    //TODO: we need to add some interfaces with @FunctionalInterface thing, to persistence servi9ces install some kind of listeners/interceptors to persist data to db(input/output/logs)
    public TransactionOutput run(String code, TransactionContext<TransactionInput> ctx) {
        ExecutableTransaction executable = registry.resolve(code);
        //TODO: it's only a concept, real implementation can be different
        return executable.execute(ctx.payload());
    }
}
```

Key properties:

* **No persistence** — runners never touch the database.
* **No Temporal awareness** — in `REFLECTED` mode a runner calls the implementation directly;
  in `GENERATED` mode it may start a Temporal workflow, but the runner's caller-visible API
  remains identical.
* **Registry-based** — the runner never knows the concrete class; it looks up the `Executable*`
  contract by string code.

#### Services

Services wrap runners with the same input/output contract but add the persistence envelope:

```java
@Service
public class EventService {
    private final EventRunner eventRunner;
    private final WorkflowExecutionRepository workflowRepo;
    private final EventExecutionRepository eventRepo;

    //TODO: to create ctx we need a `dsl-api/src/main/java/cbs/dsl/evaluator/ContextCreator.java`
    public EventResult execute(String eventCode, EventContext<EventInput> ctx) {
        // 1. Persist execution shell
        WorkflowExecution wf = workflowRepo.create(eventCode, ctx);
        EventExecution ev = eventRepo.create(wf.getId(), eventCode, ctx);
        //TODO: add here some listeners/interceptors to context object

        // 2. Run (delegate to runner — no Temporal or DB logic here)
        EventContext<EventResult> result = eventRunner.run(eventCode, ctx);

        //TODO: we need to add a async support by default(a temporal promises for generated flow, and completable future for reflective)

        // 3. Persist result and status
        ev.setResult(result);
        ev.setStatus(result.isSuccess() ? COMPLETED : FAULTED);
        eventRepo.save(ev);

        return result;
    }
}
```

Because service and runner share the same execution contract, switching between them is transparent:

```java
// QA unit test — no DB, no Temporal, for a dry-run check
EventContext<EventResult> r = eventRunner.run("LOAN_DISBURSEMENT", ctx);

// Production endpoint — full audit trail
EventContext<EventResult> r = eventService.execute("LOAN_DISBURSEMENT", ctx);
```

The same split applies to every component kind:

| Component   | Runner (no persistence) | Service (with persistence) | Temporal Artifact       |
|-------------|-------------------------|----------------------------|-------------------------|
| Event       | `EventRunner`           | `EventService`             | Workflow + Activity     |
| Transaction | `TransactionRunner`     | `TransactionService`       | Activity                |
| Condition   | `ConditionRunner`       | `ConditionService`         | Activity                |
| Helper      | `HelperRunner`          | `HelperService`            | **None** (plain code)   |
| Workflow    | `WorkflowRunner`        | `WorkflowService`          | **None** (DSL concept)  |
| MassOp      | `MassOpRunner`          | `MassOperationService`     | **None** (out of scope) |

#### Contracts (`Executable*` interfaces)

Code-generated classes must implement a contract so the registry can store them uniformly:

//TODO: we need to check that correspondnet classes exists

| Generated artifact | Contract interface      | Registry              | Temporal scope          |
|--------------------|-------------------------|-----------------------|-------------------------|
| Event              | `ExecutableEvent`       | `EventRegistry`       | Workflow + Activity     |
| Transaction        | `ExecutableTransaction` | `TransactionRegistry` | Activity                |
| Condition          | `ExecutableCondition`   | `ConditionRegistry`   | Activity                |
| Helper             | `ExecutableHelper`      | `HelperRegistry`      | **None** (plain logic)  |
| Workflow           | `ExecutableWorkflow`    | `WorkflowRegistry`    | **None** (DSL concept)  |
| Mass operation     | `ExecutableMassOp`      | `MassOpRegistry`      | **None** (out of scope) |

These contracts live in `dsl-api`.  The generated Layer-3 classes implement them, and the Layer-1
`*Definition` wrappers also implement them (or adapt to them) so that `REFLECTED` mode uses the
same registry and runner code as `GENERATED` mode.

#### How the pieces fit together (main flow)

```
Backend developer
  │
  ▼
Writes @DslComponent class (ExampleTransaction)
  │
  ▼
Gradle build → Layer 1 generates ExampleTransactionDefinition
               + registers it in TransactionRegistry
  │
  ▼
Business analyst
  │
  ▼
Adds BE jar as dependency, sees ExampleTransaction in registry
Writes DSL that references ExampleTransaction
  │
  ▼
Gradle build → Layer 2 generates EventDefinition
               Layer 3 generates Temporal workflow + ExecutableEvent impl
               + registers in EventRegistry
  │
  ▼
QA / API consumer
  │
  ├─ TestEndpoint:  eventRunner.run("LOAN_DISBURSEMENT", input) → no DB
  └─ Endpoint: eventService.execute("LOAN_DISBURSEMENT", input) → DB records created
```

This architecture keeps business logic, orchestration rules, execution engine, and persistence
strictly separated.

---

### 13.6 Registry Wiring at Startup

At application startup, the framework wires everything together without reflection-based classpath scanning.

**Layer-3 Temporal Specification registration**

```java
// SpecDefinitionRegistry registry = new GeneratedSpecificationRegistry();
// activityManager = new ActivityManager(registry);
// workflowManager = new WorkflowManager(registry, workflowClient);
```

The generated `SpecificationGenerator` produces a single `GeneratedSpecificationRegistry` that implements
`SpecDefinitionRegistry`. It enumerates every discovered workflow/activity as explicit `registerActivity` /
`registerWorkflow` calls. This is both faster than scanning and deterministic — the set of registered workflows exactly matches
the set of `@DslComponent` + DSL files present at compile time.

**Layer-1 definition registration**

`ImplRegistryAutoConfiguration` creates the `DslRegistry` bean and populates it via SPI:

```java
@Bean
public DslRegistry dslRegistry(SpringDslComponentResolver resolver) {
    DslRegistry registry = new DslRegistry();
    registry.setComponentResolver(resolver);
    SpiImplRegistryLoader.loadInto(registry, resolver);
    return registry;
}
```

`SpiImplRegistryLoader` discovers every `ImplRegistrationProvider` (the generated
`GeneratedImplRegistrations` class) and invokes `provider.register(registry, resolver)`.  The
resolver is passed through to every generated `*Definition` wrapper, enabling `SPRING` model
components to be looked up from the `ApplicationContext`.

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

| Layer              | Author                | Artifact                                                      | Temporal Awareness                                    |
|--------------------|-----------------------|---------------------------------------------------------------|-------------------------------------------------------|
| Business Function  | Java Developer        | `@DslComponent` class                                         | **None**                                              |
| DSL Orchestration  | Business Analyst      | `.java` DSL file                                              | **None**                                              |
| Definition Wrapper | Generator (Layer 1/2) | `*Definition` interface impl                                  | **None**                                              |
| Temporal Bridge    | Generator (Layer 3)   | `*Workflow`, `*Activity`                                      | **Full** — `@WorkflowInterface`, `@ActivityInterface` |
| Execution Runner   | Framework Developer   | `EventRunner`, `TransactionRunner`, `HelperRunner`, `*Runner` | **None** — registry lookup, plain Java invocation     |
| Registry Wiring    | Generator (Layer 3)   | `GeneratedSpecificationRegistry` via `SpecDefinitionRegistry` | **Full** — `Worker.register...`                       |

The generated code is the **glue** that lets business authors and developers remain completely ignorant of
Temporal, while the engine gains retries, queues, sagas, and durable execution for free.

---


### 13.9 Preview / Dry-Run Execution

Preview is a lightweight, **persistence-free** execution path used by business analysts and QA to
validate DSL logic before production deployment. It is intentionally simple:

- **No Layer-3 codegen** — preview runs against `*Definition` instances directly (Layer 1 + 2 only).
- **No Temporal** — there is no workflow stub, no activity stub, no task queue.
- **No DB** — `*Service` classes are bypassed; runners do not create `workflow_execution`,
  `event_execution`, or transition rows.
- **`preview()` instead of `execute()`** — every `*Function` and `*Definition` contract exposes
  `preview()`.  For transactions this is the developer-implemented dry-run logic; for helpers,
  events, workflows, and mass operations the default delegates to `execute()` unless overridden.

**Execution chain**

//TODO: Check next chain, read code and actualize flow with runners

```
QA / BA / Linter
  |
  v
EventRunner.preview(eventCode, ctx)      <-- no DB, no Temporal
  |
  v
EventRegistry.resolve(eventCode)
  |
  v
ExecutableEvent.preview(ctx)
  |
  +- evaluateContext()  -> HelperRunner.preview()  -> HelperRegistry -> ExecutableHelper.preview()
  +- executeTransactions() -> TransactionRunner.preview() -> TransactionRegistry -> ExecutableTransaction.preview()
  |
  v
Result returned in-memory; zero side effects.
```

The same pattern applies to `WorkflowRunner.preview()`, `MassOpRunner.preview()`,
`TransactionRunner.preview()`, `HelperRunner.preview()`, and `ConditionRunner.preview()`.

**When to use preview**

| Audience         | Use case                              | Entry point                                              |
|------------------|---------------------------------------|----------------------------------------------------------|
| Business Analyst | "What will this event compute?"       | Admin UI lint button → `EventRunner.preview()`           |
| QA               | Fast regression test without DB reset | `ShowcaseUnitTest` calling `eventDef.preview()` directly |
| Developer        | Local sanity check before commit      | `./gradlew test` with `@DslComponent` preview assertions |

---
## Referencesc

- [DSL Design](dsl-design.md) — Layer 1 & 2 details, `@DslComponent`, two-pass compilation, dual-mode DSL contexts
- [Module Structure](module-structure.md) — `dsl-codegen` file tree, dependency graph
- [Execution Model](execution-model.md) — `runEvent` vs `resumeEvent`, context hierarchy
- [Versioning](versioning.md) — strict isolation, workflow ID format, worker drain
- [Workflow Lifecycle](workflow-lifecycle.md) — state machine, `prolong()`, transitions
