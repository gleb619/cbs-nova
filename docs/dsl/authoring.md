# Authoring DSL Flows

This page describes how to write DSL definitions: the source-file format, the fluent builder API, typed vs
parameter-based definitions, helper/transaction/function calls, compensation, and a complete end-to-end example.

## DSL source files

Definitions live in a dedicated Gradle module, typically `dsl-examples/src/`. They are authored as **compact
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

The runtime is invoked through the `DslRuntime` bean, which is the same interface regardless of environment:

```java
// Preview
Context<LoanOut> result = dslRuntime.preview("LoanDisbursementProcess",
    Context.of(new LoanIn("C123", new BigDecimal("5000"))));

// Explain
Context<ExplainReport> reportCtx = dslRuntime.explain("LoanDisbursementProcess",
    Context.of(new LoanIn("C123", new BigDecimal("5000"))));
ExplainReport report = reportCtx.body();
System.out.println(report.getDescription());
System.out.println(report.getMermaidDiagram());

// Production run
Context<LoanOut> workflowResult = dslRuntime.run("LoanDisbursementProcess",
    Context.of(new LoanIn("C123", new BigDecimal("5000"))));
```

The concrete `dslRuntime` bean is selected by application wiring. A development profile executes `DslObject`s directly;
a production profile starts Temporal workers and routes `run(...)` through generated workflow classes.
