# Runtime Engine

This page describes the runtime abstractions (registry, runner, manager), the three operational modes, the
environment-agnostic `DslRuntime` interface, and dynamic configuration resolution.

## Registry abstraction

All DSL entities are looked up by name through a uniform registry contract:

- One common interface — for example `Registry<T>` — that stores a definition by name and can retrieve it.
- Three singleton entity-specific implementations, each accessed via static `getInstance()`:
  - `ProcessRegistry` — stores `Executable<?, ?>` Process definitions.
  - `TransactionRegistry` — stores `Executable<?, ?>` Transaction definitions.
  - `HelperRegistry` — stores `Executable<?, ?>` Helper **and Function** definitions. DSL Functions are registered here
    alongside normal `@Helper` classes.
- One adapter — `DslRegistryAdapter` — with static access `DslRegistryAdapter.getInstance()` that works with the three
  registries underneath and exposes a unified lookup API (`getProcess(name)`, `getTransaction(name)`, `getHelper(name)`,
  `getFunction(name)`).

In total the runtime has **three per-entity registries** and **one global adapter**.

## Runner abstraction

Execution of any DSL entity (preview / run / explain) goes through a uniform runner contract:

- One common interface — for example `Runner<T>` or `ExecutableRunner` — that can execute a definition against a
  `Context`.
- Three singleton entity-specific implementations, accessed via static `getInstance()`:
  - `ProcessRunner` — executes Process definitions.
  - `TransactionRunner` — executes Transaction definitions.
  - `HelperRunner` — executes Helper **and Function** definitions. Because Functions reuse the helper registry, they are
    dispatched through the same runner.
- One adapter — `DslRunnerAdapter` — with static access `DslRunnerAdapter.getInstance()` that delegates to the three
  runners underneath (`preview(...)`, `execute(...)`, `explain(...)` per entity). Function calls are routed to
  `HelperRunner`.

In total the runtime has **three per-entity runners** and **one global runner adapter**.

## Manager abstraction

Generated code does **not** talk to registries or runners directly. It uses a single, mode-aware entry point:
`GlobalManager.getInstance()`.

- `GlobalManager` exposes a typed API for every operation, for example:
  - `<IN, OUT> Context<OUT> executeProcess(String name, Context<…> ctx)`
  - `<IN, OUT> Context<OUT> previewProcess(String name, Context<…> ctx)`
  - `<IN, OUT> Context<OUT> explainProcess(String name, Context<…> ctx)`
  - `<IN, OUT> Context<OUT> runProcessDsl(DslObject dsl, Context<…> ctx)`
  - equivalents for Transactions (`executeTransaction`, `previewTransaction`, `explainTransaction`,
    `compensateTransaction`)
  - equivalents for Helpers/Functions (`runHelper`, `previewHelper`, `explainHelper`).
- `GlobalManager` delegates internally to three per-entity managers:
  - `ProcessManager`
  - `TransactionManager`
  - `HelperManager`
- Each per-entity manager owns its registry and runner. For example, `HelperManager` uses `HelperRegistry` and
  `HelperRunner` for both Helpers and Functions.
- The manager layer is also responsible for mode selection: it decides whether a Transaction call should go to a
  Temporal activity stub (Run) or be executed directly (Preview/Explain), and whether helper/function calls should be
  local.

This structure keeps generated `*Definition` classes free of direct registry/runner wiring.

## Operational modes

### Run mode (Production)

- The generated `*Definition` classes and their Temporal interfaces are used to start workers.
- `WorkflowClient` and `WorkerFactory` are configured with the task queues defined in the DSL.
- Processes are started via a `WorkflowClient` workflow stub with the appropriate typed input `Context`.
- Inside generated code, `GlobalManager.getInstance()` dispatches to the correct per-entity registry and runner; in
  production, transaction execution delegates to real Temporal activity stubs while helper/function calls remain local.
- Full Temporal guarantees (durability, retries, versioning) apply.
- **Compensation:** if a Process or any of its compensatable Transactions declares a `.compensation(...)` block, the
  generated workflow delegates to `GlobalManager.runProcessWithCompensation(...)`, which records transaction compensations
  and runs them in reverse order, followed by the process-level compensation block. Compensation activity failures
  follow the parent transaction's retry policy.

### Preview mode (dry-run)

- No Temporal cluster is needed.
- The compiled `DslObject`s are executed directly using the same `Context` contract, helper classes, and function
  definitions.
- `runTransaction` calls do not actually invoke Temporal activities; `GlobalManager` resolves the DSL Transaction
  definition and executes it directly through `TransactionRunner`.
- `runHelper` calls work normally through `GlobalManager.getInstance().runHelper(...)`.
- The entire execution runs synchronously and returns the final `Context<OUT>` (or throws an exception).
- **Compensation:** Preview can simulate failures (for example by throwing from a selected step) and execute the
  matching compensation blocks in the same reverse-order Saga logic, giving authors a fast way to verify rollback paths.

### Explain mode

- Identical to Preview mode, but additionally:
  - Generates a **natural-language description** of the execution flow (e.g., “Process LoanDisbursementProcess starts;
    calls helper riskAssessment and function formatCustomerMessage; then executes transactions KYC_CHECK, DEBIT_FUNDING;
    if KYC passes, completes with success; on failure, compensates DEBIT_FUNDING and sends notifyFailure...”).
  - Produces a **Mermaid diagram** of the flow, including conditional branches, parallel executions, and compensation
    paths.
- The output is returned as a structured `ExplainReport` containing description, diagram, and execution trace.

## Mode-agnostic REST surface

Mode selection is exposed through three agnostic REST endpoints, one for each operational mode. Each endpoint accepts an entity name and a `Context<IN>` payload and delegates to an injected `DslRuntime` bean.

```java
public interface DslRuntime {
    <IN, OUT> Context<OUT> preview(String name, Context<IN> ctx);
    <IN, OUT> Context<OUT> run(String name, Context<IN> ctx);
    <IN> Context<ExplainReport> explain(String name, Context<IN> ctx);
}
```

```java
@Path("/api/dsl")
@RequiredArgsConstructor
public class DslRuntimeResource {

    private final DslRuntime dslRuntime;

    @POST
    @Path("/preview/{name}")
    public Context<?> preview(@PathParam("name") String name, Input input) {
        return dslRuntime.preview(name, Context.of(input));
    }

    @POST
    @Path("/explain/{name}")
    public Context<ExplainReport> explain(@PathParam("name") String name, Input input) {
        return dslRuntime.explain(name, Context.of(input));
    }

    @POST
    @Path("/run/{name}")
    public Context<?> run(@PathParam("name") String name, Input input) {
        return dslRuntime.run(name, Context.of(input));
    }
    
    record Input(String correlationId, Map<String, Object> params){}
}
```

The controller never decides whether it is running in development or production; that choice is made by the bean that
implements `DslRuntime`. For example, a development profile injects a `DevDslRuntime` that executes `DslObject`s
directly through `GlobalManager`, while a production profile injects a `ProductionDslRuntime` that starts Temporal
workers and routes `run(...)` through the generated workflow classes.

## Helper and Spring integration

Helpers declared with `@Helper` or `@SpringHelper` are wired into the runtime through generated SPI resolvers and
Spring bean registration rather than reflection.

### Annotations

- `@Helper(name = "...")` — generic helper processed by the `misc-codegen` annotation processor.
- `@SpringHelper(name = "...")` — Spring-aware meta-annotation of `@Helper`. It forces `componentModel = LAZY` and
  `creationStrategy = STANDARD`, so the helper is registered lazily and the instance is resolved from Spring.

### Code generation (`misc-codegen`)

`backend/dsl-platform/misc-codegen/src/main/java/cbs/nova/misc/codegen/HelperSpiProcessor` emits two generated classes
per module:

- `GeneratedHelperResolver` — registers helpers with `GlobalManager`, honoring:
  - `componentModel`: `STANDARD` (eager instance) or `LAZY` (`Supplier`-deferred).
  - `creationStrategy`: `STANDARD` (resolve through `HelperInstanceResolver`) or `FACTORY` (`new X()` directly).
- `GeneratedHelperInstanceResolver` — creates instances:
  - `FACTORY` strategy: `new X()`.
  - `STANDARD` strategy: delegates to `instanceResolver.resolve(X.class)`.

`@SpringHelper` classes always emit `STANDARD` creation strategy, so their instances resolve through the runtime
`HelperInstanceResolver` rather than direct `new X()`. Classes without a public no-arg constructor are omitted from
`GeneratedHelperInstanceResolver` and must be provided by Spring.

### Runtime resolution (`DslAutoConfiguration`)

`DslAutoConfiguration` exposes a `HelperInstanceResolver` bean implemented by
`SpringOrGeneratedHelperInstanceResolver`. Resolution order is:

1. Spring bean (`SpringBeanHelperInstanceResolver`).
2. Generated factories loaded via `java.util.ServiceLoader`.
3. Otherwise throw `IllegalStateException`.

There is **no reflection fallback**: if neither Spring nor the generated factories can provide the helper, resolution
fails.

### Spring bean registration

`SpringHelperBeanDefinitionRegistrar` scans the Spring auto-configuration base packages for `@SpringHelper` classes
and registers each one as a singleton Spring bean. The registrar is imported by `SpringHelperAutoConfiguration`, which is
imported by `DslRootAutoConfiguration`.

### Registry

`HelperRegistry` implementations (for example `DefaultHelperRegistry`) store helper suppliers as
`Supplier<Executable<?, ?>>` and invoke the supplier on lookup. `HelperManager` implements `HelperRegistrar` and
forwards `register(...)` calls to the registry, supporting both direct `Executable` and `Supplier<Executable>`
registrations.

## Dynamic configuration

Temporal-specific settings (task queues, timeouts, retry policies) are declared in the DSL builders and are usually
externalized through property placeholders (e.g., `${temporal.queue.loan}`). The actual resolution is centralized in a *
*manager** component — for example `ConfigurationManager` or the corresponding methods on `GlobalManager` — that exposes
dedicated resolver interface methods such as `resolveTaskQueue(...)`, `resolveTimeout(...)`, and
`resolveRetryPolicy(...)`. Each method delegates to a `ConfigurationResolver` implementation that reads from system
properties, environment variables, a configuration server, or any other source.

Because the manager owns the resolver contracts, the same DSL can be deployed to different environments (dev, staging,
prod) with different queue names, timeouts, etc., without changing the DSL source.
