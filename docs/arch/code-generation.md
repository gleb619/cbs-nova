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
│          Executable* contract implementations (ExecutableEvent, ExecutableTransaction, …) │
│          Generated registries (EventRegistry, TransactionRegistry, HelperRegistry,    │
│                               ConditionRegistry, WorkflowRegistry, MassOpRegistry)   │
│  Tool:   TransactionCodeGenerator, EventCodeGenerator, HelperCodeGenerator,       │
│          WorkflowCodeGenerator, ConditionCodeGenerator,                             │
│          MassOperationCodeGenerator, WorkflowRegistryGenerator,                     │
│          ActivityRegistryGenerator                                                │
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

#### 13.3.2 Helper → Activity Bridge

Helpers follow the same adapter pattern as transactions but use `HelperInput` / `HelperOutput` types.
They are typically invoked from the `context {}` block of an event, although they may also appear inside
`transactions {}` when a transaction needs pre-flight data.

//TODO: Redo next table, only Events/Transactions will have a temporal support, other entities not. Events become a temporal workflow, a transactions become a temporal activities.
//TODO: Helpers doesnt got a support of temporal at all, thewy just a simple code to evaluate.

| Generated Class          | Type                       | Purpose                                                                                                                                               |
|--------------------------|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{Code}Activity`         | `@ActivityInterface`       | Temporal contract for helper execution.                                                                                                               |
| `{Code}HelperDefinition` | Definition + Activity impl | **Adapter:** implements both `HelperDefinition` and `{Code}Activity`. `HelperInput` → typed helper input → `HelperOutput`. Hosts the DSL via `dsl()`. |

Consider a helper that fetches an exchange rate:

```java
// Developer writes this
@DslComponent(code = "GET_EXCHANGE_RATE", type = DslImplType.HELPER)
public class GetExchangeRateHelper implements HelperFunction<RateInput, RateOutput> {
    @Override
    public HelperContext<RateOutput> execute(HelperContext<RateInput> input) { ... }
}

//TODO add here generated definition here, without any temporal stuff

**Helper invocation from a `context` block**

The BA writes:

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
    ...
```

//TODO: work here too, due new changes, next block is outdated

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

Events become Temporal **Workflows** because they orchestrate multiple activities (transactions), transactions itself must do business(call helpers, evaluate conditions)
and must survive process restarts. So if app will crash, temporal restore state, and continue execution. The code generated code must fit requirement of deterministic execution of helpers/conditions.

The key insight of the CBS-Nova generator is that **the DSL itself is executable Java code**.  The BA writes
`.java` files containing lambdas (`context {}`, `transactions {}`, `finish {}`).  These lambdas are
**re-used in two modes**:

//TODO: Check again that block, that Context exists, and we have interfaces for them

| Mode                | When it runs    | `ctx` / `scope` implementation | Purpose                                                                                |
|---------------------|-----------------|--------------------------------|----------------------------------------------------------------------------------------|
| **Code-generation** | Compile time    | `CodegenEnrichmentContext`     | Records which helpers / transactions are referenced so Layer 3 knows what to generate. |
| **Runtime**         | Temporal worker | `TemporalEnrichmentContext`    | Actually calls generated activity stubs.                                               |

Because the lambda body is pure Java that only invokes methods on `ctx` or `scope`, the **same source text**
can be copied into the generated workflow class unchanged.  Only the backing implementation of the
context/scope object differs.

//TODO: We thought to create one interface and two different contexts, for compile time they just register execution to understand what will be called. In runtime another impl that really call business logic. It similar to mickot how it handle it's proxies, and verify that action was performed.

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
        scope.step("DISBURSE");
    })
    .finish((ctx, ex) -> { /* optional */ })
    .build();
```

**Generated workflow**

The generator emits a workflow interface and a combined definition/workflow class. The definition class
hosts the DSL via `dsl()` and also implements the Temporal workflow contract.

```java
@WorkflowInterface
public interface LoanDisbursementWorkflow {
    @WorkflowMethod(name = "LOAN_DISBURSEMENT")
    EventContext<EventResult> execute(EventContext<EventInput> ctx);
}

// Generated — do not edit
public class LoanDisbursementEventDefinition implements EventDefinition, LoanDisbursementWorkflow {

    private final LoanDisbursementEvent function;
    private final EventWorkflowOrchestrator orchestrator;

    public LoanDisbursementEventDefinition(DslComponentResolver resolver) {
        this(resolver, null);
    }

    public LoanDisbursementEventDefinition(EventWorkflowOrchestrator orchestrator) {
        this(null, orchestrator);
    }

    public LoanDisbursementEventDefinition(DslComponentResolver resolver, EventWorkflowOrchestrator orchestrator) {
        this.function = resolver != null ? resolver.resolve(LoanDisbursementEvent.class) : new LoanDisbursementEvent();
        this.orchestrator = orchestrator;
    }

    @Override
    public String getCode() { return "LOAN_DISBURSEMENT"; }

    @Override
    public EventContext<EventOutput> execute(EventContext<EventInput> ctx) { ... }

    @Override
    public DslObject dsl() {
      //TODO: Use `ParsedDsl` from `DslCompiler` and set whole dsl here
    }

}
```

At runtime, the `dsl()` method lets evaluators access the original DSL blocks without running the full
lifecycle:

```java
TransactionDefinition txDef = registry.resolveTransaction("DEBIT_FUNDING_ACCOUNT");
transactionEvaluator.evaluate(txDef.dsl().context(), ctx);
```

The `dsl()` config is a lazy object: it stores references to the DSL lambdas and evaluates them on demand.
This is critical for matching the original BA-authored configuration when running partial evaluations
or previews.

**Design notes**

//TODO: next block is out of date, changes needd based on real code

* `TemporalTransactionsScope` implements `TransactionsScope`. Its `step(...)` overloads accept
  lambdas that invoke activity stubs and return a `StepHandle` backed by a `Promise`.  `await(...)`
  becomes `Promise.allOf(...).get()`.
* `TemporalEnrichmentContext` implements `EnrichmentContext`. Its `helper(...)` method delegates
  to the generated `HelperActivity` stub, which in turn delegates to `HelperRunner`.
* Because the DSL block is copied **as source text**, the BA can use any local variables,
  conditionals, or helper calls — the generator does not need to parse the AST beyond identifying
  the block boundaries.
* The generated workflow is **persistence-agnostic**.  DB writes, version isolation, and transition
  logging are handled by `EventService` when it calls `EventRunner` (see §13.5).

---

#### 13.3.4 Layer-1 Definition Wrappers and `componentModel`

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

When an API call `POST /api/events/execute` arrives, the engine follows the runner/service bridge.
Business classes are never invoked directly by the controller.

```
HTTP POST /api/events/execute
    │
    ▼
EventController
    │
    ▼
EventService.execute(eventCode, ctx)
    │      1. Create workflow_execution / event_execution rows
    │      2. Delegate execution to EventRunner
    ▼
EventRunner.run(eventCode, ctx)
    │      lookup ExecutableEvent in EventRegistry by code
    ▼
ExecutableEvent.execute(ctx)
    │      ├─ GENERATED mode: Temporal workflow (LoanDisbursementEventWorkflow)
    │      └─ REFLECTED mode: direct in-memory execution
    ▼
Generated workflow / reflective wrapper
    │      ├─ evaluateContext()  → calls HelperActivity stub → HelperRunner
    │      └─ executeTransactions() → calls TransactionActivity stub → TransactionRunner
    ▼
TransactionRunner.run("KYC_CHECK", ctx)
    │      lookup ExecutableTransaction in TransactionRegistry
    ▼
ExecutableTransaction.execute(ctx)
    │      JSON → typed POJO adaptation
    ▼
Developer-written @DslComponent class.execute(typedInput)
    │      pure business logic
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
├── WorkflowRunner.java       ← resolves ExecutableWorkflow from WorkflowRegistry
├── TransactionRunner.java    ← resolves ExecutableTransaction from TransactionRegistry
├── HelperRunner.java         ← resolves ExecutableHelper from HelperRegistry
├── ConditionRunner.java      ← resolves ExecutableCondition from ConditionRegistry
└── MassOpRunner.java         ← resolves ExecutableMassOp from MassOpRegistry

starter/src/main/java/cbs/nova/service/
├── EventService.java         ← EventRunner + DB persistence (workflow_execution, event_execution, transition_log)
├── WorkflowService.java      ← WorkflowRunner + DB persistence
├── TransactionService.java   ← TransactionRunner + execution artifact persistence
├── HelperService.java        ← HelperRunner + invocation logging
├── ConditionService.java     ← ConditionRunner + evaluation logging
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

| Component   | Runner (no persistence) | Service (with persistence) |
|-------------|-------------------------|----------------------------|
| Event       | `EventRunner`           | `EventService`             |
| Workflow    | `WorkflowRunner`        | `WorkflowService`          |
| Transaction | `TransactionRunner`     | `TransactionService`       |
| Helper      | `HelperRunner`          | `HelperService`            |
| Condition   | `ConditionRunner`       | `ConditionService`         |
| MassOp      | `MassOpRunner`          | `MassOperationService`     |

#### Contracts (`Executable*` interfaces)

Code-generated classes must implement a contract so the registry can store them uniformly:

//TODO: we need to check that correspondnet classes exists

| Generated artifact     | Contract interface      | Registry              |
|------------------------|-------------------------|-----------------------|
| Event workflow         | `ExecutableEvent`       | `EventRegistry`       |
| Workflow orchestration | `ExecutableWorkflow`    | `WorkflowRegistry`    |
| Transaction activity   | `ExecutableTransaction` | `TransactionRegistry` |
| Helper activity        | `ExecutableHelper`      | `HelperRegistry`      |
| Condition evaluator    | `ExecutableCondition`   | `ConditionRegistry`   |
| Mass operation         | `ExecutableMassOp`      | `MassOpRegistry`      |

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

**Layer-3 Temporal registration**

```java
// GeneratedEventRegistry.registerAll(worker);
// GeneratedTransactionRegistry.registerAll(worker);
// GeneratedHelperRegistry.registerAll(worker);
```

These generated registry classes enumerate every discovered workflow/activity class as explicit `register...`
calls.  This is both faster than scanning and deterministic — the set of registered workflows exactly matches
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
| Registry Wiring    | Generator (Layer 3)   | `Generated*Registry` (Event, Transaction, Helper, …)          | **Full** — `Worker.register...`                       |

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
