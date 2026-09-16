# Temporal DSL Orchestration Engine — Backend Architecture

This project is a **declarative Java DSL for authoring Temporal workflows and activities** without writing Temporal
boilerplate. Business flows are expressed as small, versioned definitions in a dedicated Gradle module; a custom DSL
compiler turns them into production-ready Temporal classes at build time.

This document is the high-level backend companion to [architecture.md](architecture.md) and
[architecture-ui.md](architecture-ui.md). Implementation details live in [docs/dsl/](dsl/). The
rationale behind foundational calls (Temporal, the helper SPI) is recorded in [docs/adr/](adr/).
The platform baseline (Spring Boot 4 / Jackson 3 / Java 25) is recorded in [ADR 0005](adr/0005-platform-baseline-spring-boot-4-jackson-3.md).

## What the DSL is for

The system gives non-developers and developers a shared, lightweight authoring surface for distributed orchestrations.
It introduces four constructs that share the same execution contract:

| Construct       | Temporal mapping       | Where it lives                            | Purpose                                                               |
|-----------------|------------------------|-------------------------------------------|-----------------------------------------------------------------------|
| **Process**     | Temporal Workflow      | DSL module (`backend/dsl-starter/dsl-examples/src/dsl/*.java`)      | Orchestrates a sequence of steps; defines the business flow           |
| **Transaction** | Temporal Activity      | DSL module (`backend/dsl-starter/dsl-examples/src/dsl/*.java`)      | Executes a single, idempotent, retryable action                       |
| **Function**    | None (local helper)    | DSL module (`backend/dsl-starter/dsl-examples/src/dsl/*.java`)      | Lightweight reusable logic; no Temporal code is generated             |
| **Helper**      | Plain Java class/logic | Normal Java modules (`src/main/java/...`) | Reusable business logic invoked from Processes/Transactions/Functions |

See [DSL Constructs & Execution Contract](dsl/constructs.md), [Authoring DSL Flows](dsl/authoring.md), and
[Compile-time Code Generation](dsl/codegen.md).

## Operational modes

1. **Run** — executes generated workflows/activities against a Temporal cluster.
2. **Preview (dry-run)** — executes DSL definitions directly, without Temporal, for fast local validation.
3. **Explain** — preview mode that also returns a human-readable description and a Mermaid diagram.

See [Preview Mode (dry-run)](dsl/preview-mode.md) and [Runtime Engine](dsl/runtime.md#operational-modes).
For the architectural rationale, alternatives considered, and the mechanism behind the three modes (`DslRuntime`,
pipes, `DispatchStage`, `HelperInterceptor` / `FakeHelperInterceptor`, capture `BeanPostProcessor`s), see
[ADR 0004](adr/0004-preview-dry-run-explain-modes.md).

## High-level architecture

```
┌─────────────────────────────────────────┐
│  DSL source files (`backend/dsl-starter/dsl-examples/src/dsl/`) │
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

- **API-key filter** — `cbs.nova.starter.web.ApiKeyAuthFilter` is opt-in: `ApiKeyAuthFilterConfiguration` only registers the filter beans for `/api/*` when `cbs.dsl.auth.enabled=true`. When enabled and `cbs.dsl.auth.api-key` is a non-blank string, every request must carry the exact value in the `X-Api-Key` header; otherwise the filter returns `401 UNAUTHORIZED` with a JSON `ErrorResponse`. With `enabled=false` (the default) the filter beans are not created and `/api/*` is anonymous. To prevent the silent-disablement footgun — api-key configured but `enabled` left at its default — a startup WARN fires from `ApiKeyAuthMisconfigurationWarning` whenever `api-key` is non-blank while `enabled=false`.
- **Rate limiting** — `cbs.security.ratelimit.*` (class `CbsSecurityRateLimitProperties`, filter `cbs.nova.starter.web.RateLimitFilter`) defaults to disabled. Setting `cbs.security.ratelimit.enabled=true` turns on an in-memory token-bucket limiter keyed by client IP (`X-Forwarded-For` first hop, falling back to remote address). Defaults are capacity `20` and refill `5.0` tokens per second. Only mutating routes are limited: `POST /api/dsl/run/**`, `POST /api/dsl/preview/**`, `POST /api/dsl/explain/**`, `POST /api/dsl/reload`, `POST /api/dsl/drafts/*/save`, `POST /api/dsl/drafts/*/publish`, `DELETE /api/dsl/drafts/*`, and `POST /api/executions/*/cancel`. All `GET` routes and actuator paths are exempt. A rejected request receives `429 Too Many Requests` with a `Retry-After` header.
- **OIDC / JWT resource-server** — `cbs.security.oidc.enabled` (class `CbsSecurityOidcProperties`, default `false`) switches from the permissive filter chain to a JWT resource-server. When enabled, the default `protectedPaths` (`/api/dsl/**` and `/api/executions/**`) require a valid `Authorization: Bearer <jwt>`; the default `permitAllPaths` (`/actuator/health/**`) stay anonymous, as do the springdoc/OpenAPI endpoints. The JWT decoder itself is bootstrapped by Spring Boot from `spring.security.oauth2.resourceserver.jwt.issuer-uri` (the compose stack points it at the Keycloak realm described in `app/compose/auth.yml`).
- **httpCall SSRF guard** — the `httpCall` helper validates every outbound URL before a request is built (`cbs.nova.starter.security.OutboundUrlValidator`, config `cbs.dsl.helper.http-call`): a scheme allowlist (`allowed-schemes`, default `["https", "http"]`), a default-on private-address block (`block-private-addresses`, default `true` — rejects loopback / link-local / site-local / any-local / multicast targets such as cloud-metadata endpoints), and an optional host allowlist (`allowed-hosts`, exact or `*.suffix`). The default-on address block is a **behaviour change**: DSLs calling internal hosts now fail until the flag is relaxed or the host is allowlisted. Redirect-following (`NORMAL`/`ALWAYS`) re-validates the final URI post-hoc but cannot prevent a followed redirect to a blocked address (TOCTOU) — use `NEVER` for untrusted targets. Details: [`helpers.md`](dsl/helpers.md#outbound-url-validation-ssrf-guard).

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

### Production secure-default profile (T413)

Activate with `--spring.profiles.active=production` (or `SPRING_PROFILES_ACTIVE=production`). The starter ships `backend/dsl-starter/starter/src/main/resources/application-production.yml` which raises three DEFAULTS — **not** overrides — so the profile yml sits between `application.yml` and env vars / `-D` / `TestPropertySource`:

| Knob                                                  | Default in profile | What it does |
|-------------------------------------------------------|--------------------|--------------|
| `cbs.dsl.auth.enabled`                                | `true`             | Registers the `ApiKeyAuthFilter` against `/api/*`. Pair with `cbs.dsl.auth.api-key=…` or stored keys via `POST /api/dsl/auth/keys` (T410). |
| `cbs.security.ratelimit.enabled`                      | `true`             | Token-bucket limiter on mutating DSL routes (capacity `20`, refill `5.0/s`; same defaults as opt-in). |
| `cbs.security.oidc.enabled`                           | `true`             | Switches from the permissive chain to the OIDC JWT resource-server chain. |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri`| `${OIDC_ISSUER_URI:}` | Issuer URI (Keycloak / generic OIDC). **Must be supplied by the deployment** (env, secret, `-D`). |

**Precedence — the escape hatches work exactly because env / cmdline / `@TestPropertySource` outrank the profile yml.** Examples for a single deploy that wants to opt a guard back out:

```bash
CBS_DSL_AUTH_ENABLED=false              # X-Api-Key off for this deploy only
CBS_SECURITY_RATELIMIT_ENABLED=false    # rate-limit off for this deploy only
OIDC_ISSUER_URI=https://idp.example/realms/cbs-nova
SPRING_PROFILES_ACTIVE=production
```

`application.yml` cannot override `application-production.yml` (lower precedence). The documented contract is that the production profile yml is the **floor**, env / cmdline / explicit deployment overrides are the **ceiling**, and tests using `properties=` / `@TestPropertySource` sit above this file just like env vars do.

**Fail-fast.** `ProductionSecurityPostureValidator` runs as a `SmartInitializingSingleton` once the context is fully wired. When `production` is active and the issuer URI is blank, the context fails to refresh with an `IllegalStateException` that names the exact property and the env var (`OIDC_ISSUER_URI`) the operator must set. Dev / default / explicit-test profiles never hit this check.

**Startup posture log.** `SecurityPostureReporter` emits one block at startup (production profile only) summarising the actual guard states — `api-key guard`, `rate-limit guard`, `OIDC resource-server`, `OIDC issuer URI configured` (boolean — the URI itself is masked in `ProductionSecurityPostureValidator`), and `RBAC` (T408 phase 1; stays independently opt-in — production does **not** force RBAC on). Block is at `INFO` when every guard is on, at `WARN` when an operator flipped an escape hatch so the deviation is loud at boot. Dev / default profiles are intentionally quiet.

## Observability & operations

- **Metrics (Micrometer)** — The starter publishes run-path and preview-path metrics to any `MeterRegistry` bean. Preview/explain runs are instrumented by `MetricsStage`: counters `dsl.preview.calls` (tagged by `kind`) and `dsl.preview.external.calls` (tagged by `type`), and timer `dsl.preview.duration` (tagged by `mode` and `process`). Production runs are instrumented by `TemporalDslProcessService`: timer `dsl.run.duration` and counters `dsl.run.count` and `dsl.run.cancel`, all tagged by `processName` and `status`. The retention purger additionally emits `dsl.runs.purged` and `dsl.run.transactions.purged` counters.
- **Tracing (OpenTelemetry)** — `TracingConfiguration` builds an OpenTelemetry SDK only when an OTLP endpoint is configured via `cbs.nova.tracing.otlp.endpoint` or the standard `OTEL_EXPORTER_OTLP_ENDPOINT` env var; otherwise it installs a no-op. Spans are batched to the OTLP HTTP trace exporter, and `OpenTelemetryContextPropagator` carries the W3C `traceparent` context into DSL executions. The SDK installs `W3CTraceContextPropagator` for cross-process propagation (W3C `tracecontext` + `baggage`).

### Tracing in compose (default-on)

In `app/compose/app.yml` the `spring-app` service sets `OTEL_EXPORTER_OTLP_ENDPOINT` and `CBS_NOVA_TRACING_OTLP_ENDPOINT` to `http://otel-collector:4318/v1/traces` by default, so a `make up` starts exporting spans out of the box. Outside compose both env vars are unset and tracing is a no-op (zero behavior change for non-compose users).

Pipeline:

```
spring-app ──OTLP HTTP──▶ otel-collector:4318 ──OTLP gRPC──▶ jaeger:4317
                                                          └─▶ Jaeger UI :16686
```

End-to-end span chain when a request flows BFF → backend → Temporal:

1. BFF `$fetch` / Nitro handler span (`http.client`)
2. Spring MVC `DispatcherServlet` server span (`HTTP POST /api/dsl/preview/{name}`)
3. `OpenTelemetryHelper` / `TemporalDslProcessService` dispatch span (`dsl.dispatch`)
4. Temporal workflow / activity spans (carried via `OpenTelemetryContextPropagator` in `TemporalConfiguration.setContextPropagators(...)`; the same `traceparent` is used as the Temporal `WorkflowId` correlation key)
5. The request-scope `correlation_id` MDC key (set from `X-Correlation-Id` / `X-Request-Id`, T384) is added to the active span as the `correlation_id` attribute so a trace can be joined back to the BFF log line.

View in Jaeger at `http://localhost:16686` → service `spring-app`.

Verify programmatically:

```bash
make trace-smoke   # curls one preview, then queries Jaeger /api/traces
```

The target degrades gracefully when the stack is down: it prints `trace-smoke: stack not running (start with docker compose ... up), skipping` and exits 0 — it never hangs and never hard-fails a dev machine.

#### "Tracing is a no-op" troubleshooting checklist

If `make trace-smoke` reports no traces in Jaeger (or Jaeger shows no `spring-app` service), check, in order:

1. **Endpoint env set on the running container?** `docker compose exec spring-app printenv OTEL_EXPORTER_OTLP_ENDPOINT` must return `http://otel-collector:4318/v1/traces`. If empty, the env did not inherit from `app/compose/app.yml`.
2. **OTLP exporter on the runtime classpath?** `libs.opentelemetry.exporter.otlp` (1.48.0, pinned via `backend/gradle/libs.versions.toml`) must be on the runtime classpath of the deployable. `app/Dockerfile` copies the Spring Boot fat jar — verify with `unzip -l app/build/libs/*.jar | grep opentelemetry-exporter-otlp`. Without this dep, the env var is inert.
3. **Collector reachable from the app container?** `docker compose exec spring-app wget -qO- http://otel-collector:13133/status || curl http://otel-collector:13133/status` must return 200 (the collector's health-check extension).
4. **Jaeger reachable from the host?** `curl http://localhost:16686/api/services` must list `spring-app`. If not, the collector is dropping the pipeline — inspect with `docker compose logs otel-collector`.
5. **Was a request actually executed?** A span is only exported when `TracingConfiguration` builds a real SDK (endpoint set) AND a span is recorded — make a request first (`make seed` then `curl -X POST http://localhost:3000/api/v1/dsl/preview/seed-hello-world -H 'Content-Type: application/json' -d '{}'`).
6. **Sampling / network policy.** `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0` (compose default) means every span is sampled. Behind a corporate proxy, `OTEL_EXPORTER_OTLP_ENDPOINT` must use a hostname the container can resolve — prefer the compose service name `otel-collector`, not `localhost`.

Outside compose (e.g. running the backend via `make backend`), set `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` and run an OTel collector + Jaeger on the host to see spans.
- **Health** — `/actuator/health` exposes a `dsl` component via `DslHealthIndicator`, reporting registry counts (`processes`, `transactions`, `helpers`). When a `WorkflowServiceStubs` bean is present, `TemporalHealthProbe` adds a `temporal` detail with `reachable`, `target`, `configuredTaskQueues`, and `error`. `cbs.health.temporal.fail-status` controls the outcome when Temporal is unreachable: `NONE` (default) keeps the indicator `UP` with `reachable=false`; `DOWN` makes the actuator report `DOWN` so compose/Kubernetes readiness probes gate on Temporal state. The gRPC probe timeout is `cbs.health.temporal.timeout` (default `PT2S`).
- **Input validation** — `cbs.runtime.input-validation.enabled` (default `true`, class `InputValidationProperties`) enables server-side JSON-schema validation of record inputs on `run`, `preview`, and `explain` via `InputValidator`. Non-record inputs are not shape-validated.
- **Payload caps** — `cbs.nova.starter.web.DslPayloadSizeValidator` checks incoming `POST /api/dsl/run/**` and `POST /api/dsl/preview/**` bodies against `cbs.runs.max-input-bytes` (default 1 MiB, class `DslRunsProperties`); oversize requests are rejected with `413 Payload Too Large` before any workflow is submitted. Persisted run outputs are bounded by `cbs.runs.max-output-bytes` and truncated rather than failing the run.
- **Preview execution timeout** — `cbs.nova.preview.execution.timeout-ms` (default `20000`, class `CbsNovaPreviewProperties`) bounds preview/explain execution. `DispatchStage` submits the actual DSL dispatch to the `cbsNovaPreviewDispatchExecutor`; if it does not complete in time, the future is cancelled with interrupt, the `cbs.nova.preview.timeout.count` counter is incremented, and the pipeline returns `PreviewErrorCode.PREVIEW_TIMEOUT`. HTTP handlers translate that to `504 GATEWAY_TIMEOUT` with error code `PREVIEW_TIMEOUT`. Setting the timeout to `0` disables the executor path and runs inline.
- **Retention purger** — `DslRunRetentionPurger` is scheduled only when `cbs.runs.retention` is a positive duration (class `DslRunRetentionProperties`; default `0`, disabled). It runs every `cbs.runs.purge-interval` (default `PT1H`) and deletes finished `dsl_runs` rows older than the retention in batches of `cbs.runs.purge-batch-size` (default `500`). It deletes each run's `dsl_run_transactions` child rows in the same batch window as the parent row.
- **Compile diagnostics** — `POST /api/dsl/reload` and `POST /api/dsl/drafts/{name}/publish` surface compiler diagnostics in the response when DSL compilation fails. `DslReloadHandler` caps diagnostics at 20 per failure; `DslDraftHandler` forwards them in `DraftResponse.diagnostics()` without failing the publish itself.
- **Graceful shutdown** — `backend/dsl-starter/starter/src/main/resources/application.yml` sets `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 30s`, so on `SIGTERM` the embedded server stops accepting new connections and lets in-flight `run`/`preview`/`explain` requests finish. `app/compose/app.yml` sets `stop_grace_period: 45s` on the `spring-app` service to give Docker time before `SIGKILL`. Workers are stopped at `SmartLifecycle` phase `Integer.MAX_VALUE` before the web server drain; long-running requests that outlive the shutdown phase are still cut.

Run attribution and correlation are stored on the `dsl_runs` table: migrations `V5__dsl_runs_triggered_by.sql` and `V6__dsl_runs_correlation_id.sql` add `triggered_by` and `correlation_id` columns. See [Runtime Engine — Run idempotency](dsl/runtime.md#run-idempotency) and [Correlation id](dsl/runtime.md#correlation-id) for the header semantics.

### Scheduling

The starter ships a thin REST CRUD surface over [Temporal Schedules](https://docs.temporal.io/workflows#schedule) so a published DSL definition can be triggered on a cron without writing a Temporal client. Every schedule created here starts a fresh workflow execution per fire and routes through the same `TemporalDslProcessService.startProcess` path as a manual `POST /api/dsl/run/{name}`, so each fire produces exactly one row in `dsl_runs`.

#### What it is

REST CRUD over Temporal Schedules, registered by [`DslScheduleRouterConfiguration`](../../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/router/DslScheduleRouterConfiguration.java) and handled by [`DslScheduleHandler`](../../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/controller/DslScheduleHandler.java) + [`DslScheduleService`](../../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/service/DslScheduleService.java). The whole router is `@ConditionalOnBean(ScheduleClient.class)`, so the routes vanish in non-Temporal deployments — Temporal-gated surface, by design.

#### Exact routes

Sourced from `DslScheduleRouterConfiguration`:

| Verb | Path | Handler | Purpose |
|---|---|---|---|
| `GET` | `/api/dsl/schedules` | `DslScheduleHandler.list` | List schedules created by this service (ids prefixed `sched-`). Paginated via `?limit=&offset=`; envelope is `PageResponse<ScheduleSummary>` (see [roadmap § Epic 1 / pagination convention](roadmap.md)). |
| `POST` | `/api/dsl/schedules` | `DslScheduleHandler.create` | Create a schedule that fires the definition's workflow on the given cron. Returns `201` with `CreateScheduleResponse{scheduleId, definition, cron}` or `400` / `404` / `409` (see `RouterOperation` annotations). |
| `DELETE` | `/api/dsl/schedules/{definition}` | `DslScheduleHandler.delete` | Delete the schedule for `{definition}`. Idempotent: `200 {deleted:true}` whether the schedule existed or not. |

The BFF exposes matching proxies under [`frontend/admin-ui-plugin/server/api/v1/dsl/schedules/`](../../frontend/admin-ui-plugin/server/api/v1/dsl/schedules/) (`index.get.ts`, `index.post.ts`, `[definition].delete.ts`); curl against `http://localhost:3000/api/v1/dsl/schedules*` reaches the same backend. See the recipe in the runbook: [Schedule a definition](runbook.md#schedule-a-definition).

#### Request / response shapes

From [`ScheduleModels`](../../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/model/ScheduleModels.java):

```java
public record CreateScheduleRequest(
    String definition,            // required; must match ^[A-Za-z0-9._-]{1,120}$
    String cron,                  // required
    @Nullable String timezone,    // optional; IANA TZ id; default UTC
    @Nullable Object input,       // optional; workflow input; default {}
    @Nullable String note) {}     // optional; user note on the schedule

public record ScheduleSummary(
    String scheduleId,            // "sched-<definition>"
    String definition,
    String cron,
    String timezone,
    @Nullable String note,
    @Nullable String nextRunAt,   // Instant.toString() of next fire, or null
    boolean paused) {}            // always false in the current API (see Known gaps)

public record CreateScheduleResponse(
    String scheduleId,
    String definition,
    String cron) {}
```

The schedule id format is fixed: `sched-` + `definition`. The pattern `^[A-Za-z0-9._-]{1,120}$` is enforced by `DslScheduleService.scheduleIdFor(...)` so the id can be embedded in the Temporal schedule id without escaping surprises.

#### How a fired schedule triggers a DSL run

Traced from `DslScheduleService.create(...)`:

1. `definition` is resolved against `GlobalManager.findGeneratedProcess(definition)`; missing → `404 DefinitionNotFoundException`.
2. `timezone` (default `UTC`) is validated via `ZoneId.of(timezone)`; bad zone → `400 IllegalArgumentException("Invalid timezone: …")`.
3. `ScheduleActionStartWorkflow` is built with:
   - workflow type = `descriptor.temporalInterface()`,
   - arguments = `new DslTemporalProcessRequest<>("scheduled", input)` — `"scheduled"` is a payload marker, **not** the Temporal Workflow id,
   - `WorkflowOptions` set only the `taskQueue` from the descriptor (no fixed workflow id — Temporal assigns `<scheduleId>-<scheduled-time>` per fire, per the service Javadoc).
4. `ScheduleSpec` is built with `setCronExpressions(List.of(cron))` and `setTimeZoneName(timezone)`.
5. `SchedulePolicy` is fixed:
   - `Overlap = SCHEDULE_OVERLAP_POLICY_SKIP` — if the previous fire is still running, the new fire is skipped (no parallel runs).
   - `CatchupWindow = Duration.ofMinutes(1)` — fires missed during downtime are caught up only within 1 minute of the missed time.
6. `scheduleClient.createSchedule(scheduleId, schedule, ScheduleOptions.newBuilder().build())`; existing id → `409 ScheduleConflictException`.

When the schedule fires, Temporal starts the workflow on the descriptor's task queue. That workflow executes through the same generated dispatch path as a manual run, calling `TemporalDslProcessService.startProcess(...)`, which generates a fresh `runId` via `contextFactory.generateRunId()` and writes one `dsl_runs` row per fire. Because there is no HTTP request and no Spring Security context on the Temporal worker thread, `RunIdentityResolver.resolve()` returns `null` — so **`triggered_by` is `NULL` on every scheduled run**. The Temporal Workflow id (`<scheduleId>-<scheduled-time>`) is visible in Temporal UI as the way to trace a fire back to its schedule; the `dsl_runs.run_id` is a fresh UUID per fire.

`DslScheduleHandler` writes `dsl_audit` rows for `SCHEDULE_CREATE` (with `details={definition, cron}`) and `SCHEDULE_DELETE`; audit is opportunistic via `ObjectProvider<DslAuditService>` and is a no-op when no audit bean is present (e.g. no `DataSource` configured).

#### Auth posture

The schedule surface sits under `/api/*`, so:

- **API-key filter** (`ApiKeyAuthFilterConfiguration` → `/api/*`) gates the route when `cbs.dsl.auth.enabled=true`; missing/invalid `X-Api-Key` → `401 UNAUTHORIZED`. With `enabled=false` (default) the filter is not registered and the route is anonymous.
- **RBAC filter** (`RbacFilterConfiguration` → `/api/*`) gates the route when `cbs.dsl.auth.rbac.enabled=true`. From `RbacAuthorizationFilter.RULES`:
  - `POST /api/dsl/schedules` → requires `Role.OPERATOR`.
  - `DELETE /api/dsl/schedules/*` → requires `Role.OPERATOR`.
  - `GET /api/dsl/schedules` → defaults to `Role.VIEWER` (no explicit rule; reads always default to `VIEWER` per `requiredRole`).
  - Service-to-service API-key callers are mapped to `Role.ADMIN` by `RoleResolver`, so an API key satisfies every schedule route regardless of the explicit rule.
- **OIDC / JWT resource-server** (`cbs.security.oidc.enabled=true`) requires a valid JWT on the same `/api/dsl/**` path; RBAC then resolves the role from the configured claim (default `roles`, with `scope` / `scp` fallback for OIDC-standard conventions).

#### Temporal ScheduleSpec cron format

`ScheduleSpec.setCronExpressions(List.of(cron))` accepts the **standard 5-field Unix cron** the SDK ships with (`minute hour day-of-month month day-of-week`); the existing test (`DslScheduleRouterReachabilityTest`) writes `"0 9 * * *"`, and `DslScheduleService.create` passes the user string through verbatim. Differences from classic POSIX cron:

- **No seconds field** in our code path. Temporal's `ScheduleSpec` SDK supports an optional 6-field variant (`seconds minute hour …`) but the API never emits a leading-seconds cron — caller-supplied 5-field expressions are forwarded as-is.
- **Timezone is separate**, not in the expression. The cron is interpreted in `setTimeZoneName(timezone)`, which the API validates as an IANA `ZoneId`. Default is `UTC` (`StarterConstants.DEFAULT_TIMEZONE = "UTC"`).
- **Range / step syntax** follows Temporal's parser (same as standard cron `* / , -` with Temporal's own range rules). When in doubt, treat the value as the canonical Temporal SDK input and validate by listing the schedule and reading back `cron` + `nextRunAt`.

#### Known gaps (not currently exposed / unclear)

- **Pause / unpause is not exposed.** `GET /api/dsl/schedules` returns a `paused` flag but no route sets it. There is no `PATCH` / `POST /pause` / `POST /unpause` in `DslScheduleRouterConfiguration`. Operators who need to stop a schedule today must `DELETE /api/dsl/schedules/{definition}` (idempotent) and re-`POST` it later, losing the workflow id mapping.
- **No update / modify endpoint.** The cron and timezone are immutable after creation; changing the schedule requires delete + recreate. The service does not call `updateSchedule(...)`.
- **Schedule id derived only from definition.** Re-creating a schedule for the same definition always collides on `sched-<definition>` (caught as `409 ScheduleConflictException`). There is no way to have two coexisting schedules for the same definition.
- **`triggered_by` is `NULL` for scheduled runs.** Confirmed by tracing: `RunIdentityResolver.resolve()` cannot read a Spring Security context or a request attribute from a Temporal worker thread, so it returns `null`. Operators querying `SELECT … FROM dsl_runs WHERE triggered_by IS NULL` will see scheduled runs; joining back to the originating schedule requires the Temporal Workflow id (`<scheduleId>-<scheduled-time>`) from Temporal UI.
- **Catchup window is fixed at 1 minute** (`SchedulePolicy.catchupWindow = Duration.ofMinutes(1)`). A Temporal outage longer than 1 minute silently drops the missed fires — they do not backfill when Temporal recovers.
- **Audit is best-effort.** `DslScheduleHandler.audit(...)` swallows the absence of a `DslAuditService` bean (no DataSource). A schedule create/delete against an unaudited deployment will succeed but leave no `dsl_audit` row.
- **`DslTemporalProcessRequest.runId` payload field is `"scheduled"`.** This is a payload marker carried into the workflow body, not the `dsl_runs.run_id` (which is freshly generated per fire by `contextFactory.generateRunId()`). It's a name collision with the run-id concept and may confuse anyone reading generated workflow code.

Definition-version attribution (T492): migration `V8__dsl_runs_definition_hash.sql` adds a nullable `definition_hash` column, stamped at run submission (`RunDefinitionHash`, called from `TemporalDslProcessService.startProcess`) and exposed as `ExecutionDto.definitionHash`. **This is DESCRIPTOR identity, not full logic identity** — it is the same sha256 over the Jackson-serialized `DslDescriptor` (taskQueue / version / timeouts) that the preview cache keys on, so two functionally different definitions with the same descriptor collide. It is null for historical rows and for runs whose descriptor cannot be resolved (never a run failure). A true content hash computed at publish/reload time is a planned Epic 5 follow-up.

See [Starter Configuration Reference](dsl/configuration.md) for the full key tables, and
[Operator Incident Runbook](runbook.md) for first-response playbooks (Temporal disconnect,
Keycloak outage, purger/reconciliation, BFF 5xx, helper catalog) keyed to these knobs.

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
