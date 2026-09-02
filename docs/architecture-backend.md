# Temporal DSL Orchestration Engine — Backend Architecture

This project is a **declarative Java DSL for authoring Temporal workflows and activities** without writing Temporal
boilerplate. Business flows are expressed as small, versioned definitions in a dedicated Gradle module; a custom DSL
compiler turns them into production-ready Temporal classes at build time.

This document is the high-level backend companion to [architecture.md](architecture.md) and
[architecture-ui.md](architecture-ui.md). Implementation details live in [docs/dsl/](dsl/). The
rationale behind foundational calls (Temporal, the helper SPI) is recorded in [docs/adr/](adr/).

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

## Security

The starter layers three independent, opt-in guards on the DSL REST surface. All three are off by default, so a plain starter behaves exactly like the historical anonymous implementation until an operator turns a knob.

- **API-key filter** — `cbs.nova.starter.web.ApiKeyAuthFilter` is registered for `/api/*` by `ApiKeyAuthFilterConfiguration`, but it only enforces authentication when `dsl.auth.api-key` is a non-blank string. When configured, every request must carry the exact value in the `X-Api-Key` header; otherwise the filter returns `401 UNAUTHORIZED` with a JSON `ErrorResponse`.
- **Rate limiting** — `cbs.security.ratelimit.*` (class `CbsSecurityRateLimitProperties`, filter `cbs.nova.starter.web.RateLimitFilter`) defaults to disabled. Setting `cbs.security.ratelimit.enabled=true` turns on an in-memory token-bucket limiter keyed by client IP (`X-Forwarded-For` first hop, falling back to remote address). Defaults are capacity `20` and refill `5.0` tokens per second. Only mutating routes are limited: `POST /api/dsl/run/**`, `POST /api/dsl/preview/**`, `POST /api/dsl/explain/**`, `POST /api/dsl/reload`, `POST /api/dsl/drafts/*/save`, `POST /api/dsl/drafts/*/publish`, `DELETE /api/dsl/drafts/*`, and `POST /api/executions/*/cancel`. All `GET` routes and actuator paths are exempt. A rejected request receives `429 Too Many Requests` with a `Retry-After` header.
- **OIDC / JWT resource-server** — `cbs.security.oidc.enabled` (class `CbsSecurityOidcProperties`, default `false`) switches from the permissive filter chain to a JWT resource-server. When enabled, the default `protectedPaths` (`/api/dsl/**` and `/api/executions/**`) require a valid `Authorization: Bearer <jwt>`; the default `permitAllPaths` (`/actuator/health/**`) stay anonymous, as do the springdoc/OpenAPI endpoints. The JWT decoder itself is bootstrapped by Spring Boot from `spring.security.oauth2.resourceserver.jwt.issuer-uri` (the compose stack points it at the Keycloak realm described in `app/compose/auth.yml`).

Request filtering order (lower numeric order runs first):

```
API-key filter (Ordered.HIGHEST_PRECEDENCE + 1)
    ↓
Rate-limit filter (Ordered.HIGHEST_PRECEDENCE + 2)
    ↓
Spring SecurityFilterChain (Ordered.LOWEST_PRECEDENCE) — OIDC resource-server when enabled
    ↓
RouterFunction handler
```

See [Runtime Engine — Auth and ops notes](dsl/runtime.md#auth-and-ops-notes) for the REST auth details, idempotency, and correlation headers, [`app/compose/auth.yml`](../app/compose/auth.yml) and `app/compose/keycloak/cbs-nova-realm.json` for local Keycloak setup, and [Starter Configuration Reference](dsl/configuration.md) for the full property tables.

## Observability & operations

- **Metrics (Micrometer)** — The starter publishes run-path and preview-path metrics to any `MeterRegistry` bean. Preview/explain runs are instrumented by `MetricsStage`: counters `dsl.preview.calls` (tagged by `kind`) and `dsl.preview.external.calls` (tagged by `type`), and timer `dsl.preview.duration` (tagged by `mode` and `process`). Production runs are instrumented by `TemporalDslProcessService`: timer `dsl.run.duration` and counters `dsl.run.count` and `dsl.run.cancel`, all tagged by `processName` and `status`. The retention purger additionally emits `dsl.runs.purged` and `dsl.run.transactions.purged` counters.
- **Tracing (OpenTelemetry)** — `TracingConfiguration` builds an OpenTelemetry SDK only when an OTLP endpoint is configured via `cbs.nova.tracing.otlp.endpoint` or the standard `OTEL_EXPORTER_OTLP_ENDPOINT` env var; otherwise it installs a no-op. Spans are batched to the OTLP HTTP trace exporter, and `OpenTelemetryContextPropagator` carries the W3C `traceparent` context into DSL executions.
- **Health** — `/actuator/health` exposes a `dsl` component via `DslHealthIndicator`, reporting registry counts (`processes`, `transactions`, `helpers`). When a `WorkflowServiceStubs` bean is present, `TemporalHealthProbe` adds a `temporal` detail with `reachable`, `target`, `configuredTaskQueues`, and `error`. `cbs.health.temporal.fail-status` controls the outcome when Temporal is unreachable: `NONE` (default) keeps the indicator `UP` with `reachable=false`; `DOWN` makes the actuator report `DOWN` so compose/Kubernetes readiness probes gate on Temporal state. The gRPC probe timeout is `cbs.health.temporal.timeout` (default `PT2S`).
- **Input validation** — `cbs.runtime.input-validation.enabled` (default `true`, class `InputValidationProperties`) enables server-side JSON-schema validation of record inputs on `run`, `preview`, and `explain` via `InputValidator`. Non-record inputs are not shape-validated.
- **Payload caps** — `cbs.nova.starter.web.DslPayloadSizeValidator` checks incoming `POST /api/dsl/run/**` and `POST /api/dsl/preview/**` bodies against `cbs.runs.max-input-bytes` (default 1 MiB, class `DslRunsProperties`); oversize requests are rejected with `413 Payload Too Large` before any workflow is submitted. Persisted run outputs are bounded by `cbs.runs.max-output-bytes` and truncated rather than failing the run.
- **Preview execution timeout** — `cbs.nova.preview.execution.timeout-ms` (default `20000`, class `CbsNovaPreviewProperties`) bounds preview/explain execution. `DispatchStage` submits the actual DSL dispatch to the `cbsNovaPreviewDispatchExecutor`; if it does not complete in time, the future is cancelled with interrupt, the `cbs.nova.preview.timeout.count` counter is incremented, and the pipeline returns `PreviewErrorCode.PREVIEW_TIMEOUT`. HTTP handlers translate that to `504 GATEWAY_TIMEOUT` with error code `PREVIEW_TIMEOUT`. Setting the timeout to `0` disables the executor path and runs inline.
- **Retention purger** — `DslRunRetentionPurger` is scheduled only when `cbs.runs.retention` is a positive duration (class `DslRunRetentionProperties`; default `0`, disabled). It runs every `cbs.runs.purge-interval` (default `PT1H`) and deletes finished `dsl_runs` rows older than the retention in batches of `cbs.runs.purge-batch-size` (default `500`). It deletes each run's `dsl_run_transactions` child rows in the same batch window as the parent row.
- **Compile diagnostics** — `POST /api/dsl/reload` and `POST /api/dsl/drafts/{name}/publish` surface compiler diagnostics in the response when DSL compilation fails. `DslReloadHandler` caps diagnostics at 20 per failure; `DslDraftHandler` forwards them in `DraftResponse.diagnostics()` without failing the publish itself.
- **Graceful shutdown** — `backend/dsl-starter/starter/src/main/resources/application.yml` sets `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 30s`, so on `SIGTERM` the embedded server stops accepting new connections and lets in-flight `run`/`preview`/`explain` requests finish. `app/compose/app.yml` sets `stop_grace_period: 45s` on the `spring-app` service to give Docker time before `SIGKILL`. Workers are stopped at `SmartLifecycle` phase `Integer.MAX_VALUE` before the web server drain; long-running requests that outlive the shutdown phase are still cut.

Run attribution and correlation are stored on the `dsl_runs` table: migrations `V5__dsl_runs_triggered_by.sql` and `V6__dsl_runs_correlation_id.sql` add `triggered_by` and `correlation_id` columns. See [Runtime Engine — Run idempotency](dsl/runtime.md#run-idempotency) and [Correlation id](dsl/runtime.md#correlation-id) for the header semantics.

See [Starter Configuration Reference](dsl/configuration.md) for the full key tables.

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
