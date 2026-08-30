# Temporal DSL Orchestration Engine — Backend Architecture

This project is a **declarative Java DSL for authoring Temporal workflows and activities** without writing Temporal
boilerplate. Business flows are expressed as small, versioned definitions in a dedicated Gradle module; a custom DSL
compiler turns them into production-ready Temporal classes at build time.

This document is the high-level backend companion to [architecture.md](architecture.md) and
[architecture-ui.md](architecture-ui.md). Implementation details live in [docs/dsl/](dsl/).

## What the DSL is for

The system gives non-developers and developers a shared, lightweight authoring surface for distributed orchestrations.
It introduces four constructs that share the same execution contract:

| Construct       | Temporal mapping       | Where it lives                            | Purpose                                                               |
|-----------------|------------------------|-------------------------------------------|-----------------------------------------------------------------------|
| **Process**     | Temporal Workflow      | DSL module (`dsl-examples/src/*.java`)      | Orchestrates a sequence of steps; defines the business flow           |
| **Transaction** | Temporal Activity      | DSL module (`dsl-examples/src/*.java`)      | Executes a single, idempotent, retryable action                       |
| **Function**    | None (local helper)    | DSL module (`dsl-examples/src/*.java`)      | Lightweight reusable logic; no Temporal code is generated             |
| **Helper**      | Plain Java class/logic | Normal Java modules (`src/main/java/...`) | Reusable business logic invoked from Processes/Transactions/Functions |

See [DSL Constructs & Execution Contract](dsl/constructs.md), [Authoring DSL Flows](dsl/authoring.md), and
[Compile-time Code Generation](dsl/codegen.md).

## Operational modes

1. **Run** — executes generated workflows/activities against a Temporal cluster.
2. **Preview (dry-run)** — executes DSL definitions directly, without Temporal, for fast local validation.
3. **Explain** — preview mode that also returns a human-readable description and a Mermaid diagram.

See [Preview Mode (dry-run)](dsl/preview-mode.md) and [Runtime Engine](dsl/runtime.md#operational-modes).

## High-level architecture

```
┌─────────────────────────────────────────┐
│  DSL source files (`dsl-examples/src/`) │
│  Compact JEP-512 sources, one define()  │
└─────────────────┬───────────────────────┘
                  ▼
┌─────────────────────────────────────────┐
│  Gradle DSL module build                │
│  DSL compiler → descriptors + generated │
│  Temporal workflow/activity classes       │
└─────────────────┬───────────────────────┘
                  ▼
┌─────────────────────────────────────────┐
│  Production: generated Temporal workers │
│  (Workflow + Activity interfaces)       │
├─────────────────────────────────────────┤
│  Preview/Explain: direct DslObject      │
│  execution through GlobalManager        │
└─────────────────┬───────────────────────┘
                  ▼
┌─────────────────────────────────────────┐
│  Temporal Server / local report         │
└─────────────────────────────────────────┘
```

## Local stack

The full local stack lives in `app/docker-compose.yml`, which includes per-domain compose files under
`app/compose/` (see `app/compose/README.md` for ports and env vars). The orchestration slice provides
Temporal server (`temporal:7233`) and Temporal UI (`localhost:8233`).

## App deployment topology

`app/` is the publishable application that consumes the backend modules:

- `app/server` — Spring Boot host consuming `cbs.nova:starter` (runtime) and `cbs.nova:app-dsl` (generated DSLs).
- `app/dsl` — customer-shaped DSL module applying the `cbs.nova.dsl` Gradle plugin; publishes a jar that `app/server`
  loads via SPI.
- `app/ui` — Nuxt host mounting `@cbs/admin-ui-plugin`; its Nitro BFF proxies `/api/v1/**` to the Spring Boot app.

See [Working with DSL examples](dsl/examples.md) for the build/run flow.

## Runtime layers

Generated code talks to one facade — `GlobalManager.getInstance()` — which delegates to three layers:

- **Registry layer** — `ProcessRegistry`, `TransactionRegistry`, `HelperRegistry`.
- **Runner layer** — `ProcessRunner`, `TransactionRunner`, `HelperRunner`.
- **Manager layer** — `ProcessManager`, `TransactionManager`, `HelperManager`.

See [Runtime Engine](dsl/runtime.md) for registries, runners, managers, modes, REST surface, dynamic configuration,
and helper/Spring integration.

## Spring Boot autoconfiguration

`config.DslRootAutoConfiguration` is the single autoconfiguration entry point. It `@Import`s the starter autoconfigs,
including `DslAutoConfiguration`, `TemporalConfiguration`, `DslWorkerConfiguration`, and `SpringHelperAutoConfiguration`.
`SpringHelperAutoConfiguration` registers `@SpringHelper` classes in the auto-configuration base packages as singleton
Spring beans. See [Runtime Engine](dsl/runtime.md#helper-and-spring-integration).

## Build & run

Agent-facing commands and the full build sequence are in [backend/AGENTS.md](../backend/AGENTS.md). Quick end-to-end
verification is in the top-level [AGENTS.md](../AGENTS.md).

## Expression evaluation

DSL runtime code evaluates placeholders and small expressions through the
`cbs.nova.dsl.utils.ExpressionEvaluator` contract. The default implementation is
`cbs.nova.dsl.utils.MvelExpressionEvaluator`, backed by MVEL, in both the
platform-standalone runtime and the Spring Boot starter.

`DslConfig.expressionEvaluator()` returns a `Replaceable<ExpressionEvaluator>` so
callers can swap the evaluator at startup or in tests. `DslAutoConfiguration`
publishes an `ExpressionEvaluator` bean backed by `MvelExpressionEvaluator` and
replaces the platform default during application startup. Because the bean is
declared with `@ConditionalOnMissingBean`, a user-defined
`ExpressionEvaluator` bean takes precedence and becomes the runtime evaluator.

### Supported expressions

The default evaluator supports:

- `{variable}` and `${variable}` variable interpolation.
- Mixed text with multiple placeholders (`"sum: ${a + b}, {c}"`).
- Missing/null variables render as an empty string in interpolation contexts.
- Arithmetic: `+`, `-`, `*`, `/`, parentheses, unary minus.
- String concatenation when at least one operand is a string (`${'x' + 1}`).
- Boolean and numeric variables referenced as top-level expressions (`${flag}`).
- MVEL extras: equality/comparison (`==`, `!=`, `<`, `>`), boolean logic
  (`&&`, `||`), and `null` checks.

Numeric results are typically `Integer` or `Double`. If you need a custom
evaluator, provide an `ExpressionEvaluator` bean or call
`DslConfig.dslConfig().expressionEvaluator().replace(...)` directly.


## See also

- [DSL Constructs & Execution Contract](dsl/constructs.md)
- [Authoring DSL Flows](dsl/authoring.md)
- [Compile-time Code Generation](dsl/codegen.md)
- [Runtime Engine](dsl/runtime.md)
- [Preview Mode (dry-run)](dsl/preview-mode.md)
- [Working with DSL Examples](dsl/examples.md)
- [IDEA Plugin for DSL Editing](dsl/idea-plugin.md)
- [Starter Configuration Reference](dsl/configuration.md) — every `@ConfigurationProperties` key and its default.

## Primary goals

- **Business autonomy** — non-developers can author and modify flows without touching core Temporal code.
- **Correctness** — every workflow instance runs on the DSL version it started with.
- **Compile-time generation** — Processes and Transactions become Temporal classes during the Gradle build.
- **Dynamic worker configuration** — task queues, timeouts, and retry policies are configurable via DSL builders.
- **Reusable helpers and functions** — common logic is extracted as `@Helper` classes or `Dsl.function(...)` definitions.
- **Declarative compensation** — Processes and Transactions can define rollback/cleanup steps that run automatically on failure.
- **Preview & Explain** — fast feedback loops and living documentation without deploying to Temporal.
