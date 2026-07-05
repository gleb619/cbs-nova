# Temporal DSL Orchestration Engine — Architecture

This project is a **declarative Java DSL for authoring Temporal workflows and activities** without writing Temporal
boilerplate. Business flows are expressed as small, versioned definitions in a dedicated Gradle module; a custom DSL
compiler turns them into production-ready Temporal classes at build time.

## What the DSL is for

The system gives non-developers and developers a shared, lightweight authoring surface for distributed orchestrations.
It introduces four constructs that all share the same execution contract:

| Construct       | Temporal mapping       | Where it lives                            | Purpose                                                               |
|-----------------|------------------------|-------------------------------------------|-----------------------------------------------------------------------|
| **Process**     | Temporal Workflow      | DSL module (`dsl-module/src/*.java`)      | Orchestrates a sequence of steps; defines the business flow           |
| **Transaction** | Temporal Activity      | DSL module (`dsl-module/src/*.java`)      | Executes a single, idempotent, retryable action                       |
| **Function**    | None (local helper)    | DSL module (`dsl-module/src/*.java`)      | Lightweight reusable logic; no Temporal code is generated             |
| **Helper**      | Plain Java class/logic | Normal Java modules (`src/main/java/...`) | Reusable business logic invoked from Processes/Transactions/Functions |

DSL sources are **JEP-512 compact source files**: no `class` declaration, no `public` modifier, and no package
statement. Each file exposes a `List<DslObject> define()` method built with a fluent API.

## Operational modes

Three modes let the same definition behave differently depending on environment and need:

1. **Run** — executes against a live Temporal cluster using generated workflow/activity classes.
2. **Preview (dry-run)** — executes DSL definitions directly, without Temporal, for fast local validation.
3. **Explain** — preview mode that also returns a human-readable description and a Mermaid diagram.

## High-level architecture

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                    DSL Module Source Files (`dsl-module/src/`)              │
│                                                                               │
│  ┌───────────────────────────────────┐   ┌──────────────────────────────────┐ │
│  │ LoanDisbursementProcessDsl.java   │   │ KycCheckTransactionDsl.java      │ │
│  │ List<DslObject> define() {        │   │ List<DslObject> define() {       │ │
│  │   return                          │   │   return                         │ │
│  │     Dsl.process("LoanProcess")    │   │     Dsl.transaction("KYC_CHECK") │ │
│  │       .input(LoanIn.class)        │   │       .input(KycIn.class)         │ │
│  │       .output(LoanOut.class)       │   │       .output(KycOut.class)       │ │
│  │       .execute(ctx -> { ... })    │   │       .execute(ctx -> { ... })    │ │
│  │       .buildList();                │   │       .buildList();                │ │
│  │ }                                 │   │ }                                │ │
│  └───────────────────────────────────┘   └──────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                       Gradle DSL Module Build                                  │
│                                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                    DSL Compiler                                            │  │
│  │  (source scanner + annotation processor + code generator)                   │  │
│  │                                                                            │  │
│  │  Phase 1: Scan DSL module /src, locate `define()` methods                  │  │
│  │  Phase 2: Build AST (ProcessDescriptor, TransactionDescriptor,             │  │
│  │                     FunctionDescriptor)                                    │  │
│  │  Phase 3: Validate semantics (parameters, helper refs, cycles)            │  │
│  │  Phase 4: Generate Temporal classes with `@WorkflowInterface`,           │  │
│  │           `@WorkflowMethod`, `@ActivityInterface`, `@ActivityMethod`    │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                      ▼
┌─────────────────────────────────────┐  ┌─────────────────────────────────────────┐
│     Generated Temporal Classes     │  │    Direct DSL Runtime (Preview/Explain) │
│  (Production Mode)                   │  │                                          │
│                                      │  │  • Executes `DslObject`s directly      │
│  • *ProcessWorkflow (interface)      │  │  • Uses GlobalManager                  │
│  • *ProcessDefinition                │  │  • Supports preview/explain modes      │
│  • *TransactionActivity (interface)  │  │  • No generated Temporal classes needed│
│  • *TransactionDefinition            │  │                                          │
└──────────────────────────────────────┘  └────────────────────────────────────────┘
                                       │
                                       ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                      Temporal Worker Runtime (Run Mode)                         │
│                                                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐    │
│  │  Workflow   │  │  Activity   │  │  Activity   │  │  Dynamic Task Queue │    │
│  │  Worker     │──│  Worker     │──│  Activity   │──│  Configuration      │    │
│  └─────────────┘  └─────────────┘  │  Worker     │  └─────────────────────┘    │
│                                    └─────────────┘                              │
└───────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Temporal Server                                       │
│                    (Workflow Executions + Event History)                        │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Runtime layers

The runtime is deliberately layered so generated code has a single entry point:

- **Registry layer** — `ProcessRegistry`, `TransactionRegistry`, and `HelperRegistry` store definitions by name.
  Functions declared in DSL sources are registered in `HelperRegistry` alongside normal `@Helper` classes.
- **Runner layer** — `ProcessRunner`, `TransactionRunner`, and `HelperRunner` execute definitions against typed
  `Context`s.
- **Manager layer** — `GlobalManager.getInstance()` is the only facade generated code talks to. It delegates internally
  to `ProcessManager`, `TransactionManager`, and `HelperManager`.

See [Runtime details](dsl/runtime.md) for the full contract, operational modes, and REST endpoints.

## DSL authoring, constructs, and generated code

- **[DSL constructs & execution contract](dsl/constructs.md)** — `Executable`, `Context`, and the semantics of Process,
  Transaction, Function, and Helper.
- **[Authoring DSL flows](dsl/authoring.md)** — source files, builder API, `Result` type, helper/transaction calls,
  compensation, and the full loan-disbursement example.
- **[Compile-time code generation](dsl/codegen.md)** — generated class naming/versioning, the Gradle module, the
  generation pipeline, and generated code samples.

## Primary goals

- **Business autonomy** — non-developers can author and modify flows without touching core Temporal code.
- **Correctness** — every workflow instance runs on the DSL version it started with.
- **Compile-time generation** — Processes and Transactions become Temporal classes during the Gradle build.
- **Dynamic worker configuration** — task queues, timeouts, and retry policies are configurable via DSL builders.
- **Reusable helpers and functions** — common logic is extracted as `@Helper` classes or `Dsl.function(...)`
  definitions.
- **Declarative compensation** — Processes and Transactions can define rollback/cleanup steps that run automatically on
  failure.
- **Preview & Explain** — fast feedback loops and living documentation without deploying to Temporal.

## Implementation roadmap (summary)

1. **Core DSL model & builder API** — `DslObject`, builders, typed `Context`, `Result`, `@Helper`, compensation.
2. **DSL parser & validation** — source scanner, AST, semantic validation, property placeholders.
3. **Code generation** — Temporal interfaces/implementations, versioned packages, Saga wiring, function registration.
4. **Runtime engine** — `DslRuntime` interface, development vs production wiring, REST endpoints.
5. **Gradle DSL module & plugin** — standard module template and optional Gradle plugin.
6. **Testing, documentation, & CI** — unit/integration tests, user guide, CI pipeline.

## Summary

The Temporal DSL Orchestration Engine turns compact Java DSL definitions into durable, observable Temporal workflows. It
unifies Processes, Transactions, Helpers, and Functions under one typed `Context`-based contract, supports declarative
compensation, and exposes the same flow through Preview, Explain, and production Run modes.
