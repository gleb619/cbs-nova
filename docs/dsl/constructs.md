# DSL Constructs & Execution Contract

All DSL entities share a single, uniform execution contract. This makes it possible to preview, run, and explain the
same definition with the same code, regardless of whether it maps to a Temporal Workflow, Activity, or a local helper.

## The uniform interface

```java
public interface Executable<IN, OUT> {
    Context<OUT> preview(Context<IN> context);
    Context<OUT> execute(Context<IN> context);
    Context<OUT> explain(Context<IN> context);
}
```

Each method receives a `Context<IN>` and returns a `Context<OUT>`.

```java
public interface Context<T> {
    T body();                       // typed input or output payload
    Map<String, Object> metadata(); // logs, trace info, metrics, etc.
    Context<T> withBody(T body);    // immutable copy with new payload
    Context<T> withMetadata(Map<String, Object> metadata);

    static <T> Context<T> of(T body) {
        return new DefaultContext<>(body, Map.of());
    }
}
```

Because `Context` is generic, every entity declares its own typed input and output records:

```java
@Json
public record HelperParamIn(String customerId) {}

@Json
public record HelperParamOut(String riskLevel) {}

@Helper(name = "riskAssessment")
public class RiskAssessmentHelper implements Executable<HelperParamIn, HelperParamOut> {

    @Override
    public Context<HelperParamOut> preview(Context<HelperParamIn> context) {
        return execute(context);
    }

    @Override
    public Context<HelperParamOut> execute(Context<HelperParamIn> context) {
        return context.withBody(new HelperParamOut("MEDIUM"));
    }

    @Override
    public Context<HelperParamOut> explain(Context<HelperParamIn> context) {
        return preview(context)
            .withMetadata(Map.of("description", "Assesses customer risk level"));
    }
}
```

The same contract applies to Processes, Transactions, and Functions.

## The four DSL constructs

| Construct       | Temporal mapping       | Where it lives                            | Purpose                                                     |
|-----------------|------------------------|-------------------------------------------|-------------------------------------------------------------|
| **Process**     | Temporal Workflow      | DSL module (`dsl-examples/src/*.java`)      | Orchestrates a sequence of steps; defines the business flow |
| **Transaction** | Temporal Activity      | DSL module (`dsl-examples/src/*.java`)      | Executes a single, idempotent, retryable action             |
| **Function**    | None (local helper)    | DSL module (`dsl-examples/src/*.java`)      | Lightweight reusable logic; no Temporal code is generated   |
| **Helper**      | Plain Java class/logic | Normal Java modules (`src/main/java/...`) | Reusable business logic invoked from DSL constructs         |

### Process

A Process maps to a Temporal Workflow. It orchestrates Transactions, Helpers, and Functions, and declares:

- a typed input/output pair or a parameter schema,
- a task queue,
- an optional human-readable `version` label,
- an optional `.compensation(...)` block.

### Transaction

A Transaction maps to a Temporal Activity. It performs a single, well-defined action and is durable and retryable. It
can also declare compensation so that generated workflows can run Saga rollbacks.

### Function

A Function is a DSL-declared helper. It is **not** generated into Temporal classes; it is registered in `HelperRegistry`
and executed through the same path as `@Helper` classes. Functions are intended for lightweight local computation that
does not require Temporal durability, retries, or task queues.

A Function may only call other Functions and Helpers. It cannot invoke Processes or Transactions, and it does not
support compensation.

### Helper

A Helper is a normal Java class annotated with `@Helper`. It lives outside the DSL module so it can be unit-tested,
reused, and versioned independently. Helpers are invoked from the DSL via `ctx.runHelper(...)`.

```java
package helpers;

@Helper(name = "riskAssessment")
public class RiskHelper implements Executable<RiskIn, RiskOut> {

    @Override
    public Context<RiskOut> preview(Context<RiskIn> ctx) {
        return execute(ctx);
    }

    @Override
    public Context<RiskOut> execute(Context<RiskIn> ctx) {
        String customerId = ctx.body().customerId();
        // business logic
        return ctx.withBody(new RiskOut("MEDIUM"));
    }

    @Override
    public Context<RiskOut> explain(Context<RiskIn> ctx) {
        return preview(ctx)
            .withMetadata(Map.of("description", "Risk assessment helper"));
    }
}
```

**Helper rules:**

- Must implement `Executable<IN, OUT>`.
- Its input/output records must be annotated with `@Json`.
- The `@Helper` name must be unique across **all helpers and functions** (they share the same registry).
- The DSL compiler validates every `runHelper` call for name, type compatibility, and parameter availability.

**Helper configuration**

`@Helper` supports two optional attributes:

- `componentModel` — `STANDARD` (eager instance) or `LAZY` (`Supplier`-deferred lookup).
- `creationStrategy` — `STANDARD` (instance resolved through `HelperInstanceResolver`) or `FACTORY` (direct `new X()`).

For Spring-managed helpers, use `@SpringHelper(name = "...")` instead. It is a meta-annotation of `@Helper` that
forces `componentModel = LAZY` and `creationStrategy = STANDARD`, so the helper becomes a Spring bean and is resolved
through the runtime `HelperInstanceResolver` rather than direct instantiation. See [Runtime Engine](runtime.md#helper-and-spring-integration) for the full resolution order and generated SPI wiring.

## Important scoping rules

- **Helper** classes are normal Java code and live outside the DSL module.
- **Process**, **Transaction**, and **Function** definitions are written only inside DSL module source files.
- A Process may call Transactions, Helpers, and Functions.
- A Transaction may call Helpers and Functions only.
- Helpers and Functions may call Helpers and Functions only.
- Compensation blocks may only call Helpers and Functions — not Transactions or Processes.

## JSON binding with Avaje Jsonb

All DSL model records (for Processes, Transactions, and Functions), helper parameter records, and Temporal
payload classes are annotated with `@Json` so that Avaje Jsonb can generate reflection-free serializers at compile
time.

Avaje Jsonb is a fast, reflection-free JSON binder that uses Java Annotation Processing (APT). It is GraalVM
native-image ready and supports rich types such as collections, `Optional`, streams, and `java.time` classes.

The `ModelSourcePreprocessor` automatically injects `@Json` (and the required `io.avaje.jsonb.Json` import) into DSL model files when the annotation is missing.

### Gradle dependency

```gradle
dependencies {
    implementation 'io.avaje:avaje-jsonb:3.4'
    annotationProcessor 'io.avaje:avaje-jsonb-generator:3.4'
}
```

### Basic usage

```java
import io.avaje.jsonb.Json;

@Json
public record Customer(long id, String name) {}
```

```java
Jsonb jsonb = Jsonb.builder().build();

Customer customer = new Customer(1, "Alice");
String jsonText = jsonb.toJson(customer);

Customer parsedCustomer = jsonb.type(Customer.class).fromJson(jsonText);
```
