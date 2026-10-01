# Authoring DSL Flows

This page describes how to write DSL definitions: the source-file format, the fluent builder API, typed vs
parameter-based definitions, helper/transaction/function calls, signals, compensation, and a complete end-to-end example.

## DSL source files

Definitions live in a dedicated Gradle module, typically `backend/dsl-starter/dsl-examples/src/dsl/`. They are authored as **compact
source files**:

- no `class` declaration,
- no `public` modifier,
- no package statement.

Each file exposes one `List<DslObject> define()` method. At build time (and at runtime when a source directory is
configured), the compiler preprocesses the file into a normal Java class that implements
`cbs.nova.dsl.DslCompactSource`, makes `define()` public, and then compiles it normally:

```java
import com.example.dsl.Dsl;
import com.example.dsl.DslObject;
import java.util.List;

List<DslObject> define() {
    return List.of(
        Dsl.transaction("KYC_CHECK")
            .input(KycIn.class)
            .output(KycOut.class)
            .execute(ctx -> { ... })
            .build(),

        Dsl.process("LoanDisbursementProcess")
            .input(LoanIn.class)
            .output(LoanOut.class)
            .execute(ctx -> { ... })
            .build()
    );
}
```

The module build configuration is described in [Compile-time code generation](codegen.md).

## Model imports

Model classes live under `src/models/` (one model class per file). DSL sources (and other model sources) may import
them in any of the following styles — all resolve to the same canonical generated package
`<basePackage>.<version>` (or the DSL-file-specific versioned package when `useFileNameSubPackage=true`):

```java
import <basePackage>.<ModelClass>.*;            // e.g. cbs.nova.dslexamples.BatchModels.*
import <basePackage>.<version>.<ModelClass>.*;  // e.g. cbs.nova.dslexamples.v1.BatchModels.*
import <version>.<ModelClass>.*;                // e.g. v1.BatchModels.*
import <ModelClass>.*;                          // e.g. BatchModels.*
```

Member imports work in every style, e.g. `import cbs.nova.dslexamples.v1.BatchModels.BatchIn;` or bare
`import BatchModels.BatchIn;`. Resolution rules:

- `<basePackage>.<segment>...` is always interpreted as `BASE`/`BASE_VERSION` first: if `<segment>` is a model class
  name it is treated as `BASE` even when it also equals the version segment.
- `VERSION`/`BASE_VERSION` styles require a configured (non-blank) `buildVersion`; a numeric version like `1`
  matches its normalized segment (`v1`).
- A bare `import <ModelClass>...` fails fast when no `basePackage` is configured and more than one model file
  exists — qualify the import.
- Imports that match no known model class are left untouched.

Every matched import is rewritten to the model's canonical resolved package before compilation, so compiled output
is identical regardless of the authoring style.

## Builder API

### Process

A Process defines a Temporal Workflow.

```java
public record LoanIn(String customerId, BigDecimal amount) {}
public record LoanOut(String message, boolean success) {}

Dsl.process("LoanDisbursementProcess")
    .taskQueue("loan-processing")
    .version("1.0.0")          // human-readable label
    .input(LoanIn.class)
    .output(LoanOut.class)
    .compensation(ctx -> {
        ctx.runHelper("notifyFailure", MapInput.of("customerId", ctx.body().customerId()));
        ctx.log("Loan process compensated for customer " + ctx.body().customerId());
    })
    .execute(ctx -> {
        LoanIn in = ctx.body();
        // orchestration logic
        return ctx.complete(new LoanOut("done", true));
    })
    .build();
```

### Transaction

A Transaction defines a Temporal Activity.

```java
Dsl.transaction("KYC_CHECK")
    .taskQueue("kyc")
    .retryPolicy(r -> r.maxAttempts(3))
    .startToCloseTimeout(Duration.ofSeconds(30))
    .input(KycIn.class)
    .output(KycOut.class)
    .execute(ctx -> {
        KycIn in = ctx.body();
        boolean verified = !"HIGH".equals(in.riskLevel());
        return Result.success(new KycOut(verified));
    })
    .build();
```

#### Heartbeat

Declare a `.heartbeatTimeout(Duration)` on long-running transactions so Temporal can cancel
an activity that has stopped making progress. Without it, a stuck activity runs until the
wall-clock `startToCloseTimeout` and the only signal is the final timeout. With a heartbeat
timeout, Temporal cancels the activity as soon as no heartbeat arrives within the window.

```java
Dsl.transaction("LongWorkSimulation")
    .input(LongWorkIn.class)
    .output(LongWorkOut.class)
    .startToCloseTimeout(Duration.ofSeconds(30))
    .heartbeatTimeout(Duration.ofSeconds(5))
    .execute(ctx -> {
        LongWorkIn in = ctx.body();
        int completed = 0;
        for (int i = 0; i < in.steps(); i++) {
            if (in.stepDelayMs() > 0) Thread.sleep(in.stepDelayMs());
            completed++;
        }
        return Result.success(new LongWorkOut(in.taskId(), "COMPLETED", completed));
    })
    .build();
```

The generated activity implementation (see
`backend/dsl-platform/dsl-codegen/.../TransactionCodeGenerator.java`) wraps the transaction
body in a periodic Temporal heartbeat at `heartbeatTimeout / 3`, so DSL authors do not have
to scatter `Activity.getExecutionContext().heartbeat()` calls through their transaction
logic. The wrapper is implemented by
`backend/dsl-starter/starter/.../runtime/ActivityHeartbeat.java`.

Constraints:

- `heartbeatTimeout` must be `<=` `startToCloseTimeout`. The builder rejects the opposite at
  build time (`transaction '<name>' heartbeatTimeout (PT5S) must be <= startToCloseTimeout (PT3S)`)
  because Temporal would always cancel on the wall-clock budget first and the heartbeat
  timeout would be dead code.
- Equal values are allowed (the tightest legitimate configuration). Anything in between
  gives Temporal two chances to detect a stuck activity before the wall-clock budget.
- Local execution (no Temporal worker) is unaffected: the heartbeat callback swallows the
  resulting `IllegalStateException` and the periodic task is a no-op.

#### Retry policy — cap and skip-list

A `RetryPolicy` declares `maxAttempts`, `initialInterval`, and `backoffCoefficient`. Two
optional knobs cap and short-circuit the retry chain when an upstream is slow or a failure
is deterministic:

- `maximumInterval(Duration)` caps the exponential backoff so a slow-recovering upstream
  cannot accumulate arbitrarily long waits between retries. Without it Temporal grows the
  delay to `100 × initialInterval`, which can exceed `startToCloseTimeout` and mask real
  failures as timeout cascades. Must be `>= initialInterval`.
- `nonRetryable(String...)` (or `nonRetryable(List<String>)`) lists Temporal error type
  names that should fail the activity immediately instead of consuming the retry budget.
  Use this for deterministic failures (validation, malformed payload) so the call fails fast
  and the workflow can route the cause to compensation.

Both fields are optional. When unset, Temporal's defaults apply — identical to the pre-T751
behaviour — so adding them is purely additive. They reach Temporal through
`TemporalTransactionInvoker#buildActivityOptions` as `setMaximumInterval` and
`setDoNotRetry`.

```java
Dsl.transaction("SlowValidation")
    .input(ValidationIn.class)
    .output(ValidationOut.class)
    .startToCloseTimeout(Duration.ofSeconds(30))
    .retryPolicy(new RetryPolicy(5, Duration.ofMillis(500), 2.0)
        .withMaximumInterval(Duration.ofSeconds(30))
        .withNonRetryable("ValidationException", "BadRequest"))
    .execute(ctx -> {
        ValidationIn in = ctx.body();
        if (!in.isWellFormed()) {
            throw new IllegalArgumentException("payload is invalid");
        }
        return ctx.complete(new ValidationOut("OK"));
    })
    .build();
```

Validation enforced at `new RetryPolicy(...)` time:

- `maxAttempts >= 1`.
- `backoffCoefficient > 0`.
- `maximumInterval >= initialInterval` when set.
- `nonRetryable` entries must be non-blank when set.

Wither methods (`withMaximumInterval`, `withNonRetryable`) return a new policy, leaving
the original unchanged. The legacy 3-argument constructor `new RetryPolicy(maxAttempts,
initialInterval, backoffCoefficient)` remains available so every existing DSL flow compiles
unchanged.

### Function

A Function is a lightweight DSL helper. It is not generated into Temporal classes.

```java
Dsl.function("formatCustomerMessage")
    .input(MessageIn.class)
    .output(MessageOut.class)
    .execute(ctx -> {
        MessageIn in = ctx.body();
        String text = "Customer " + in.customerId() + " requested loan of " + in.amount();
        return Result.success(new MessageOut(text));
    })
    .build();
```

## Typed vs parameter-based definitions

**Typed definitions** use `.input(...)` and `.output(...)` with `@Json` records:

```java
.input(KycIn.class)
.output(KycOut.class)
.execute(ctx -> {
    KycIn in = ctx.body();
    return Result.success(new KycOut(...));
})
```

**Parameter-based definitions** use `.parameters(...)` and receive/return `MapInput`/`MapOutput`:

```java
.parameters(reg -> {
    reg.string("customerId");
    reg.number("amount");
})
.execute(ctx -> {
    MapInput params = ctx.body();
    String customerId = (String) params.values().get("customerId");
    BigDecimal amount = (BigDecimal) params.values().get("amount");
    return Result.success(MapOutput.of("customerId", customerId, "amount", amount));
})
```

The DSL compiler validates that every parameter referenced in `execute` is registered in `.parameters(...)`.

## Calling helpers, functions, and transactions

The authoring surface uses the Avaje-backed `Result` type so authors do not need to write generic type parameters at
every call site.

### Helper / Function calls

```java
// Auto-resolved from current context
RiskOut risk = ctx.runHelper("riskAssessment").as(RiskOut.class);

// Explicit map input
MessageOut message = ctx.runHelper("formatCustomerMessage",
        MapInput.of("customerId", in.customerId(), "amount", in.amount()))
    .as(MessageOut.class);

// Untyped map output
MapOutput result = ctx.runHelper("someHelper").as(MapOutput.class);
```

When called without explicit arguments, the manager/runner layer auto-resolves the required input fields from the
current context, eliminating boilerplate for the common case where the surrounding context already carries the needed
values.

### Transaction calls

```java
// Typed transaction with explicit input
KycOut kyc = ctx.runTransaction("KYC_CHECK",
        MapInput.of("customerId", in.customerId(), "riskLevel", risk.riskLevel()))
    .as(KycOut.class);

// Parameter-based transaction, auto-resolved from current context
ctx.runTransaction("DEBIT_FUNDING");
```

### Forcing failure

```java
if (!kyc.verified()) {
    ctx.fail("KYC failed");
}
```

## Signals

A Process can declare **signals** — named, typed messages an external caller delivers to a running workflow.
Declaration happens on the builder (`.signal(name, payloadType)`), consumption happens on the
`ProcessContext` inside `.execute(...)`. Signals are Temporal-only in **Run** mode; in **Preview** /
**Hierarchy** / **Explain** they resolve from a seeded timeline (see
[Preview-mode signals](preview-mode.md#preview-mode-signals)).

Runtime design, codegen, routes, and RBAC: [Architecture — Signals (T567)](../architecture-backend.md#signals-t567).
Operator recipe: [Runbook — Signal a running process](../runbook.md#signal-a-running-process).

### Declaring a signal

```java
public <T> ProcessBuilder<I, O> signal(@NonNull String name, @NonNull Class<T> payloadType)
```

Each `.signal(...)` call appends a `SignalDescriptor(name, payloadType)` to the builder's list; the descriptors
land in both `ProcessDescriptor.signals` (drives codegen) and `ProcessDslObject.signals` (the runtime
`DslObject`) when `build()` runs. Declare every signal you intend to await — undeclared names are rejected by
the send route with `400` (`UNKNOWN_SIGNAL`), and Temporal silently drops them.

### Consuming a signal

Three default helpers on `ProcessContext<T>`:

```java
@NonNull <T> T awaitSignal(@NonNull String name, @NonNull Class<T> type);   // blocking
@Nullable <T> T getSignalPayload(@NonNull String name, @NonNull Class<T> type);  // non-blocking peek
boolean signalReceived(@NonNull String name);                              // non-blocking check
```

- `awaitSignal` blocks until the signal has been received, then returns the payload cast to `type`.
- `getSignalPayload` returns the payload if already received, otherwise `null`.
- `signalReceived` returns `true` once the signal has been received, otherwise `false`.

All three resolve the same `SignalAwaiter` from the run metadata key
`Constants.SIGNAL_AWAITER_METADATA_KEY = "cbs.nova.dsl.signalAwaiter"`, so they are mode-agnostic: the generated
Temporal workflow buffer serves `Run`, `PreviewSignalBuffer` serves the other modes.

### Worked example

Trimmed from
[`SignalProbeDsl.java`](../../backend/dsl-starter/dsl-examples/src/dsl/SignalProbeDsl.java):

```java
List<DslObject> define() {
  return Dsl.process("SignalProbe")
          .input(String.class)
          .output(String.class)
          .signal("approval", Map.class)
          .execute(ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> approval = ctx.awaitSignal("approval", Map.class);
            boolean approved = Boolean.TRUE.equals(approval.get("approved"));
            String reviewer = String.valueOf(approval.getOrDefault("reviewer", "unknown"));
            return Result.success("approved=" + approved + ":" + reviewer + ":" + ctx.body());
          })
          .buildList();
}
```

See also [`SignalBranchDsl.java`](../../backend/dsl-starter/dsl-examples/src/dsl/SignalBranchDsl.java) for the
peek-then-await pattern (`signalReceived` / `getSignalPayload` before `awaitSignal`).

### Sending a signal

Signals are delivered to a **running** execution over REST:

| Verb | Path | Purpose |
|---|---|---|
| `POST` | `/api/dsl/signals/{runId}` | Deliver a named signal. Body `SignalRequest{signalName, payload?}`. Returns `200 {runId, signalName, status:"sent"}`. |
| `GET` | `/api/dsl/queries/{runId}` | Read the buffered payloads: `200 {runId, signalState: {name → payload, ...}}`. |

Error mapping: `404 NOT_FOUND` for an unknown run id, `409 CONFLICT` when the run is not `RUNNING`,
`400` for a malformed body or an undeclared signal name. Sending requires `Role.RUNNER`; the query route
falls through to the read default (`Role.VIEWER`). Both routes are also proxied by the BFF at
`/api/v1/dsl/signals/{runId}` and `/api/v1/dsl/queries/{runId}`. A `curl` example lives in the
[runbook](../runbook.md#signal-a-running-process); the Executions-detail panel exposes the same two calls
(`data-testid="signal-panel"`).

A `200 {"status":"sent"}` confirms Temporal *accepted* the signal, not that the workflow has observed it — poll
the query route for `signalState` when you need that guarantee.

### Timeouts and absent signals

There is **no timeout overload** on the signal API — `awaitSignal(name, type)` is the only blocking form, and
in **Run** mode it waits indefinitely for the signal. Bound the wait from the outside: enforce a
workflow/activity timeout, or have the caller always deliver. The non-blocking helpers never wait, so guard
long-running flows with `signalReceived(...)` if you would rather branch than block.

Mode-specific behaviour when a signal never arrives:

- **Run** — the workflow task stays blocked on the signal; the run stays `RUNNING`. If the run is not
  `RUNNING` the send route answers `409 CONFLICT` and nothing is delivered.
- **Preview** — a signal absent from the seeded `signals` timeline fails fast with `SignalNotSeededException`,
  surfaced as preview error code `SIGNAL_NOT_SEEDED` (HTTP 422). Preview never hangs.
- **Bare in-process runs** (no pipeline installed a buffer) — `awaitSignal` throws `IllegalStateException`
  ("Signal awaiting is not available in this context"); `getSignalPayload` returns `null` and
  `signalReceived` returns `false`.

## Compensation and Sagas

Both `Process` and `Transaction` builders accept an optional `.compensation(...)` block.

In **Run** mode, the generated Temporal workflow implements a basic Saga pattern:

- Each compensatable Transaction exposes both `execute` and `compensate` activity methods.
- If a step fails, the workflow invokes compensations of completed transactions in reverse order.
- If the Process declares a compensation block, it runs after the per-transaction compensations (or alone if none
  exist).
- Compensation activities use the parent transaction's task queue, retry policy, and timeout unless overridden.

In **Preview** and **Explain** modes, compensation blocks are first-class execution paths. Preview can simulate failures
to verify rollback logic; Explain describes and diagrams the compensation flow.

## Diagnostic codes

The DSL compiler and semantic validator emit machine-readable diagnostic codes. Branching on the
`code` field is safe; the human message text may be reworded without notice.

### Semantic validation codes

These codes are produced by the semantic validator and are stable across releases:

| Code | When it fires | Example message |
|------|---------------|-----------------|
| `BLANK_PROCESS_NAME` | A Process definition has a blank name | `Process has blank name` |
| `BLANK_TRANSACTION_NAME` | A Transaction definition has a blank name | `Transaction has blank name` |
| `BLANK_FUNCTION_NAME` | A Function definition has a blank name | `Function has blank name` |
| `DUPLICATE_NAME` | Two definitions share the same name | `Duplicate name: <name>` |
| `UNKNOWN_HELPER` | A Process or Transaction references an unregistered helper | `Process '<name>' references unknown helper: <helper>` |
| `CIRCULAR_DEPENDENCY` | A cycle is detected in Function references | `Circular dependency detected involving: [<names>]` |

### Syntax-error codes

When the underlying Java compiler rejects DSL source, the raw javac code (for example
`compiler.err.cannot.find.symbol`) is passed through on `CompileDiagnostic.code` when the
compiler reports it. These identifiers are JDK-versioned: they are stable enough to branch on in
practice, but they are not a formal compatibility contract. When compilation runs remotely via
the dsl-builder service (the only compile path since the in-process javac fallback was removed,
T570), diagnostics arrive as plain message strings and `code` may be null.

## Complete loan disbursement example

### Input/output records

```java
package model;

import io.avaje.jsonb.Json;
import java.math.BigDecimal;

@Json
public record LoanIn(String customerId, BigDecimal amount) {}

@Json
public record LoanOut(String message, boolean success) {}

@Json
public record KycIn(String customerId, String riskLevel) {}

@Json
public record KycOut(boolean verified) {}

@Json
public record RiskIn(String customerId) {}

@Json
public record RiskOut(String riskLevel) {}

@Json
public record MessageIn(String customerId, BigDecimal amount) {}

@Json
public record MessageOut(String text) {}
```

### DSL file

```java
import com.example.dsl.Context;
import com.example.dsl.Dsl;
import com.example.dsl.DslObject;
import com.example.dsl.MapInput;
import com.example.dsl.MapOutput;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import model.*;

List<DslObject> define() {
    return List.of(
        Dsl.transaction("KYC_CHECK")
            .taskQueue("kyc")
            .retryPolicy(r -> r.maxAttempts(3))
            .startToCloseTimeout(Duration.ofSeconds(30))
            .input(KycIn.class)
            .output(KycOut.class)
            .execute(ctx -> {
                KycIn in = ctx.body();
                boolean verified = !"HIGH".equals(in.riskLevel());
                return Result.success(new KycOut(verified));
            })
            .build(),

        Dsl.transaction("DEBIT_FUNDING")
            .taskQueue("accounting")
            .retryPolicy(r -> r.maxAttempts(5))
            .startToCloseTimeout(Duration.ofSeconds(60))
            .parameters(reg -> {
                reg.string("customerId");
                reg.number("amount");
            })
            .compensation(ctx -> {
                MapInput in = ctx.body();
                ctx.runHelper("reverseDebit", MapInput.of(
                    "customerId", in.values().get("customerId"),
                    "amount", in.values().get("amount")
                ));
                ctx.log("Reversed debit for customer " + in.values().get("customerId"));
            })
            .execute(ctx -> {
                MapInput params = ctx.body();
                String customerId = (String) params.values().get("customerId");
                BigDecimal amount = (BigDecimal) params.values().get("amount");
                return Result.success(MapOutput.of(
                    "customerId", customerId,
                    "debitId", "D-123",
                    "amount", amount
                ));
            })
            .build(),

        Dsl.function("formatCustomerMessage")
            .input(MessageIn.class)
            .output(MessageOut.class)
            .execute(ctx -> {
                MessageIn in = ctx.body();
                String text = "Customer " + in.customerId() + " requested loan of " + in.amount();
                return Result.success(new MessageOut(text));
            })
            .build(),

        Dsl.process("LoanDisbursementProcess")
            .taskQueue("loan-processing")
            .version("1.0.0")
            .input(LoanIn.class)
            .output(LoanOut.class)
            .compensation(ctx -> {
                ctx.runHelper("notifyFailure", MapInput.of("customerId", ctx.body().customerId()));
                ctx.log("Loan process compensated for customer " + ctx.body().customerId());
            })
            .execute(ctx -> {
                LoanIn in = ctx.body();

                RiskOut risk = ctx.runHelper("riskAssessment").as(RiskOut.class);
                ctx.log("Risk level: " + risk.riskLevel());

                MessageOut message = ctx.runHelper("formatCustomerMessage",
                        MapInput.of("customerId", in.customerId(), "amount", in.amount()))
                    .as(MessageOut.class);
                ctx.log("Message: " + message.text());

                KycOut kyc = ctx.runTransaction("KYC_CHECK",
                        MapInput.of("customerId", in.customerId(), "riskLevel", risk.riskLevel()))
                    .as(KycOut.class);
                if (!kyc.verified()) {
                    ctx.fail("KYC failed");
                }

                ctx.runTransaction("DEBIT_FUNDING");

                return ctx.complete(new LoanOut(message.text(), true));
            })
            .build(),

        Dsl.process("NotificationSender")
            .taskQueue("notifications")
            .version("1.0.0")
            .parameters(reg -> {
                reg.string("customerId");
                reg.string("channel");
                reg.string("message");
            })
            .execute(ctx -> {
                MapInput params = ctx.body();
                String customerId = (String) params.values().get("customerId");
                String channel = (String) params.values().get("channel");

                ctx.log("Sending " + channel + " to " + customerId);

                return Result.success(MapOutput.of(
                    "customerId", customerId,
                    "status", "SENT",
                    "sentAt", Instant.now().toString()
                ));
            })
            .build()
    );
}
```

### Helper class

```java
package helpers;

import com.example.dsl.Context;
import com.example.dsl.Executable;
import com.example.dsl.Helper;
import java.util.Map;
import model.RiskIn;
import model.RiskOut;

@Helper(name = "riskAssessment")
public class RiskHelper implements Executable<RiskIn, RiskOut> {

    @Override
    public Context<RiskOut> preview(Context<RiskIn> ctx) {
        return execute(ctx);
    }

    @Override
    public Context<RiskOut> execute(Context<RiskIn> ctx) {
        String customerId = ctx.body().customerId();
        return ctx.withBody(new RiskOut("MEDIUM"));
    }

    @Override
    public Context<RiskOut> explain(Context<RiskIn> ctx) {
        return preview(ctx)
            .withMetadata(Map.of("description", "Risk assessment helper"));
    }
}
```

### Runtime invocation

The runtime is invoked through the `DslRuntime` bean. The interface shape is the same regardless of
environment, but the concrete implementation is selected by application wiring (for example, a
production profile routes `run(...)` through generated Temporal workflow classes, while a local
profile executes the DSL objects directly):

```java
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.ExecutionMode;

LoanIn input = new LoanIn("C123", new BigDecimal("5000"));

// Preview — returns Result<PreviewReport>
Result<PreviewReport> previewResult = dslRuntime.preview("LoanDisbursementProcess",
    DslConfig.dslConfig().contextFactory().of(input, ExecutionMode.PREVIEW));

// Explain — returns ExplainReport directly
ExplainReport report = dslRuntime.explain("LoanDisbursementProcess",
    DslConfig.dslConfig().contextFactory().of(input, ExecutionMode.EXPLAIN));
System.out.println(report.description());
System.out.println(report.mermaidDiagram());

// Production run — returns Result<?>
Result<?> runResult = dslRuntime.run("LoanDisbursementProcess",
    DslConfig.dslConfig().contextFactory().of(input, ExecutionMode.RUN));
```

The concrete `dslRuntime` bean is selected by application wiring. A development profile executes `DslObject`s directly;
a production profile starts Temporal workers and routes `run(...)` through generated workflow classes.
