# Runtime Engine

This page describes the runtime abstractions (registry, runner, manager), the three operational modes, the
environment-agnostic `DslRuntime` interface, and dynamic configuration resolution.

> **See also:** [Configuration Reference](configuration.md) — the consolidated reference for every starter
> `@ConfigurationProperties` key (`cbs.nova.*`, `cbs.security.*`, `cbs.health.*`, `cbs.runs.*`, `dsl.*`, and the
> Temporal / server env vars), with defaults taken from the code.

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


### Preview/Explain execution timeout

Preview and explain execution time is bounded so a looping or hanging DSL construct cannot pin the
Tomcat request thread forever. The bound is configured via
`cbs.nova.preview.execution.timeout-ms` (default `20000`, `0` disables the executor path and runs
inline) and `cbs.nova.preview.execution.pool-size` (default `4`).

When enabled, the final `DispatchStage` submits the actual dispatch call to a fixed daemon pool
(`cbsNovaPreviewDispatchExecutor`) and waits up to the configured timeout. If the call does not
complete in time, the future is cancelled with interrupt, the `cbs.nova.preview.timeout.count`
Micrometer counter is incremented, and the pipeline continues with a `PreviewErrorCode.PREVIEW_TIMEOUT`
error. HTTP handlers translate that code into `504 GATEWAY_TIMEOUT` with error code
`PREVIEW_TIMEOUT`.

**Honest cancellation limitation:** interrupt stops interruptible waits (for example
`Thread.sleep`), but a pure CPU-spin loop keeps its worker thread until it exits. The JVM provides no
safe thread kill, so the pool is bounded and named (`cbs-preview-dispatch-*`) for diagnosability.

**MDC / log correlation:** dispatch workers do not inherit per-request MDC or other request-thread
context. Logs produced inside the dispatched DSL will not carry the execution run id unless context
is propagated explicitly (for example via a Spring `TaskDecorator`).
## DSL REST surface

The starter exposes the DSL surface as Spring `RouterFunction` handlers, not as a JAX-RS resource.
Each route is registered by a dedicated `@Configuration` in
`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/*RouterConfiguration.java`
and handled by the matching functional handler in `.../controller/`.
`@RouterOperation` annotations live next to the route definitions, so the live spec is available at
`/v3/api-docs` and rendered by the springdoc UI at `/swagger-ui/index.html`.

The mode-agnostic runtime contract inside the backend is the `cbs.nova.dsl.DslRuntime` interface:

```java
public interface DslRuntime {
    @NonNull Result<PreviewReport> preview(@NonNull String name, @NonNull Context<?> ctx);
    @NonNull Result<?> run(@NonNull String name, @NonNull Context<?> ctx);
    @NonNull ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx);
}
```

The HTTP handlers translate the JSON request body into a `Context` via `DslRequest` and delegate to
a `DslRuntime` bean (through `DslRuntimeService`). The concrete `DslRuntime` implementation decides
whether `run(...)` starts a Temporal workflow or executes directly; `preview(...)` and `explain(...)`
always run locally.

### Route table

All paths are relative to the application root. The reload and draft routers are gated by
`dsl.reload.enabled` and `dsl.drafts.enabled` respectively (both default `true`).

| Method | Path | Purpose | Request / Response |
|--------|------|---------|--------------------|
| POST | `/api/dsl/preview/{name}` | Dry-run a DSL process | `DslRequest` body → `PreviewReport` |
| POST | `/api/dsl/run/{name}` | Execute a DSL process with full side effects | `DslRequest` body → result object, or `422 ErrorResponse` |
| POST | `/api/dsl/explain/{name}` | Static-analysis report for a DSL process | `DslRequest` body → `ExplainReport` |
| GET | `/api/dsl/processes` | List registered process names | `NamesResponse` |
| GET | `/api/dsl/processes/{name}` | Metadata for a single process | `ProcessDetail` |
| GET | `/api/dsl/processes/{name}/diagram` | Render a diagram; optional query `format=mermaid|plantuml|bpmn` | `ProcessDiagramDto` |
| GET | `/api/dsl/transactions` | List registered transaction names | `NamesResponse` |
| GET | `/api/dsl/transactions/{name}` | Metadata for a single transaction | `TransactionDetail` |
| GET | `/api/dsl/objects/search` | Search helpers, processes, transactions, and functions | query params `name`, `type`, `description` → `HelperSearchResult[]` |
| GET | `/api/dsl/helpers` | List registered helper names | `NamesResponse` |
| GET | `/api/dsl/constructs/{name}` | Structure and generated code body of a construct | `ConstructBodyDto` |
| GET | `/api/dsl/definitions` | Flat list of all registered DSL entities | `DefinitionMetaDto[]` |
| GET | `/api/executions` | List execution runs | query params `processName`, `status`, `mode`, `limit`, `offset` → `ExecutionListResponse` |
| GET | `/api/executions/stats` | Aggregate execution statistics | query param `topProcesses` → `ExecutionStatsResponse` |
| GET | `/api/executions/{id}` | Single execution run | `ExecutionDto` |
| POST | `/api/executions/{id}/cancel` | Cancel a running execution run | `ExecutionDto` |
| POST | `/api/dsl/reload` | Reload DSL definitions from `dsl.source-dir` | `ReloadResponse` |
| POST | `/api/dsl/drafts/{name}/save` | Persist a Workbench draft | `DraftRequest` body → `DraftResponse` |
| POST | `/api/dsl/drafts/{name}/publish` | Persist as published and reload DSL | `DraftRequest` body → `DraftResponse` |
| DELETE | `/api/dsl/drafts/{name}` | Delete a Workbench draft | `DraftResponse` |

The runtime request record is `cbs.nova.starter.model.DslRequest`:

```json
{
  "body": <any>,
  "metadata": { "<key>": <value>, ... }
}
```

The draft request record is `cbs.nova.starter.model.VcsModels.DraftRequest`:

```json
{
  "name": "LoanDisbursementProcess",
  "type": "process",
  "status": "Draft",
  "version": "1.0.0",
  "taskQueue": "loan-processing"
}
```

### curl examples

```bash
# Run a process
curl -s -X POST http://localhost:8090/api/dsl/run/LoanDisbursementProcess \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: demo-run-1" \
  -d '{"body":{"customerId":"C123","amount":5000},"metadata":{"source":"ci"}}'

# Preview a process
curl -s -X POST http://localhost:8090/api/dsl/preview/LoanDisbursementProcess \
  -H "Content-Type: application/json" \
  -d '{"body":{"customerId":"C123","amount":5000}}'

# Explain a process
curl -s -X POST http://localhost:8090/api/dsl/explain/LoanDisbursementProcess \
  -H "Content-Type: application/json" \
  -d '{"body":{"customerId":"C123","amount":5000}}'

# Cancel an execution run
RUN_ID="<run-id>"
curl -s -X POST "http://localhost:8090/api/executions/${RUN_ID}/cancel"

# Reload DSL definitions from dsl.source-dir
curl -s -X POST http://localhost:8090/api/dsl/reload

# Save a Workbench draft
curl -s -X POST http://localhost:8090/api/dsl/drafts/LoanDisbursementProcess/save \
  -H "Content-Type: application/json" \
  -d '{"name":"LoanDisbursementProcess","type":"process","status":"Draft","version":"1.0.0","taskQueue":"loan-processing"}'

# Publish a Workbench draft
curl -s -X POST http://localhost:8090/api/dsl/drafts/LoanDisbursementProcess/publish \
  -H "Content-Type: application/json" \
  -d '{"name":"LoanDisbursementProcess","type":"process","status":"Published","version":"1.0.0","taskQueue":"loan-processing"}'

# Delete a draft
curl -s -X DELETE http://localhost:8090/api/dsl/drafts/LoanDisbursementProcess
```


### Publish history u0026 restore

Every successful `POST /api/dsl/drafts/{name}/publish` snapshots the previous
`.workbench/published/{name}.json` metadata marker into
`.workbench/history/{name}/{timestamp}.json` before overwriting it. Snapshots are
**metadata only** (the published `DraftRequest` record: name, type, status,
version, taskQueue); DSL source code is not captured. The number of retained
snapshots per definition is controlled by `dsl.drafts.history-limit`
(default `20`; `u003c= 0` keeps unlimited history).

| Method | Route | Behaviour |
|--------|-------|-----------|
| GET | `/api/dsl/drafts/{name}/history` | List publish history entries, newest first |
| POST | `/api/dsl/drafts/{name}/history/{timestamp}/restore` | Restore a snapshot as the current published marker and reload DSL |

### Auth and ops notes

- **API key filter** — `cbs.nova.starter.web.ApiKeyAuthFilter` is registered for `/api/*` but only
  enforces authentication when `dsl.auth.api-key` is a non-blank string. When configured, every
  request must carry the exact value in the `X-Api-Key` header; otherwise the filter returns
  `401 UNAUTHORIZED` with a JSON `ErrorResponse`.
- **OIDC / JWT** — Setting `cbs.security.oidc.enabled=true` (default `false`) switches from the
  default permissive filter chain to a JWT resource-server. By default the protected path patterns
  are `/api/dsl/**` and `/api/executions/**`; every request under those paths must carry a valid
  `Authorization: Bearer <jwt>`. `permitAllPaths` defaults to `/actuator/health/**`. Configure
  `spring.security.oauth2.resourceserver.jwt.issuer-uri` (or `jwk-set-uri`) for the JWT decoder.
- **Rate limiting** — Setting `cbs.security.ratelimit.enabled=true` (default `false`) enables an
  in-memory token-bucket rate limiter keyed by client IP. Defaults: capacity `20`, refill
  `5.0` tokens per second. Only mutating routes are limited: `POST /api/dsl/run/**`,
  `POST /api/dsl/preview/**`, `POST /api/dsl/explain/**`, `POST /api/dsl/reload`,
  `POST /api/dsl/drafts/*/save`, `POST /api/dsl/drafts/*/publish`, `DELETE /api/dsl/drafts/*`,
  and `POST /api/executions/*/cancel`. All `GET` routes and actuator paths are exempt. A rejected
  request receives `429 Too Many Requests` with a `Retry-After` header.
- **Request ID** — The runtime handlers read the optional `X-Request-Id` header and use it as the
  execution run id; `RequestIdFilter` echoes the value back in the response. If the header is
  omitted, a generated UUID is used.
- **Payload size** — Incoming `POST /api/dsl/run/**` and `POST /api/dsl/preview/**` bodies are
  validated against `cbs.runs.max-input-bytes` (default 1 MiB). Oversized payloads are rejected with
- **BFF OIDC login flow (T309)** — When the admin UI is served with an OIDC issuer configured,
  the BFF can drive a standard authorization-code + PKCE login against that provider, keeping
  tokens in httpOnly cookies and forwarding `Authorization: Bearer <access>` to the Spring
  backend. The flow is **fully inert** when no issuer is configured; default DX and tests are
  unchanged.

  Environment variables consumed by the Nuxt module / BFF:

  | Variable | Default | Purpose |
  |----------|---------|---------|
  | `AUTH_ISSUER` | *(unset)* | OIDC issuer URL, e.g. `http://localhost:8080/realms/cbs-nova`. When blank, OIDC is disabled. |
  | `AUTH_CLIENT_ID` | `cbs-nova-bff` | Client id registered at the issuer. |
  | `AUTH_CLIENT_SECRET` | *(unset)* | Confidential client secret (server-side only). |
  | `AUTH_CALLBACK_URL` | `http://localhost:3000/api/v1/auth/callback` | Absolute redirect URI registered at the issuer. |
  | `AUTH_POST_LOGOUT_REDIRECT` | `/` | Client-side path to return to after logout. |

  Public runtime flag: `useRuntimeConfig().public.authEnabled` is `true` exactly when
  `AUTH_ISSUER` is non-empty, so the UI only renders the Sign-in affordance when configured.

  BFF routes:

  | Method | Route | Behaviour |
  |--------|-------|-----------|
  | GET | `/api/v1/auth/login` | Builds PKCE + state, sets `cbs_oidc_txn`, redirects to the issuer authorization endpoint. Optional `?redirect=<same-origin-path>` is stored for post-login return. Returns `404` when OIDC is not configured. |
  | GET | `/api/v1/auth/callback` | Validates state against `cbs_oidc_txn`, exchanges the code, writes `cbs_at` + `cbs_rt`, clears the txn cookie, and redirects to the stored same-origin path (defaults to `/`). Returns `403` for state mismatch. |
  | GET | `/api/v1/auth/logout` | Best-effort OIDC end-session call with the refresh token, clears `cbs_at` + `cbs_rt`, redirects to `AUTH_POST_LOGOUT_REDIRECT`. Returns `404` when OIDC is not configured. |
  | GET | `/api/v1/auth/session` | Returns `{ authenticated: false, enabled: false }` when OIDC is disabled. Otherwise reads `cbs_at`, calls userinfo, refreshes once on `401/403`, and returns `{ authenticated: true, user }` or `401`. |

  Cookie names and flags:

  | Cookie | Purpose | Flags |
  |--------|---------|-------|
  | `cbs_oidc_txn` | Short-lived (600s) transaction state for the in-flight authorization request. | `httpOnly`, `SameSite=Lax`, `Secure` only when callback URL is HTTPS, path `/`. |
  | `cbs_at` | Access token forwarded as `Authorization: Bearer <cbs_at>`. | `httpOnly`, `SameSite=Lax`, `Secure` only when callback URL is HTTPS, path `/`, `maxAge` from token `expires_in`. |
  | `cbs_rt` | Refresh token used to silently rotate `cbs_at` on backend `401/403`. | `httpOnly`, `SameSite=Lax`, `Secure` only when callback URL is HTTPS, path `/`, `maxAge` ~30 days. |

  Header precedence: an **inbound** `Authorization` header always wins over the BFF session token.
  This lets service-to-service callers and explicit bearer tokens override the cookie-based session
  while still allowing the BFF session to fill the gap for browser traffic.

  Refresh behaviour: when `proxyToBackend` receives a backend `401` or `403`, OIDC is enabled, and a
  `cbs_rt` cookie exists, the BFF performs **one** token refresh and retries the original request
  with the new access token. If refresh fails the session is cleared and the original backend
  error is surfaced. Only one retry is attempted per proxied call.

  End-to-end recipe with the compose Keycloak:

  ```bash
  # 1. Start Postgres + Keycloak (the realm is imported on first boot).
  docker compose -f app/compose/auth.yml up -d

  # 2. Start the Spring backend with OIDC enabled.
  export SERVER_PORT=8090
  export AUTH_ISSUER=http://localhost:8080/realms/cbs-nova
  backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test
  SERVER_PORT=8090 backend/dsl-platform/gradlew -p backend/dsl-starter :starter-launcher:bootRun -x test \
    -Dcbs.security.oidc.enabled=true \
    -Dspring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/cbs-nova

  # 3. Start the Nuxt dev server.
  cd frontend
  pnpm install
  pnpm dev

  # 4. Open http://localhost:3000, click Sign in, and log in with the realm dev user:
  #    username: devuser
  #    password: devpassword
  # 5. Run a preview or any DSL operation; the BFF attaches the Bearer token automatically.
  # 6. Click Sign out to clear the BFF session.
  ```

  `413 Payload Too Large` before any workflow is submitted.

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

## Temporal UI deep-links

The admin UI's executions detail view can render a "View in Temporal" anchor on the Workflow ID row that opens the
matching Temporal Web UI page. The feature is opt-in via the `adminUiPlugin.temporalUiBaseUrl` module option (env
`TEMPORAL_UI_BASE_URL`, blank by default). When set, the link points at
`${baseUrl}/namespaces/${adminUiPlugin.temporalNamespace}/workflows/${workflowId}` — the v2 Temporal UI route shape
served at `:8233` by the local compose stack. Set `adminUiPlugin.temporalNamespace` (env `TEMPORAL_NAMESPACE`, default
`'default'`) when running against a non-default namespace.

## Temporal health gating

The starter's `DslHealthIndicator` (exposed at `/actuator/health` as the `dsl` component) reports the live reachability
of the Temporal cluster alongside the registry counts. When a `WorkflowServiceStubs` bean is present (i.e. the runtime is
configured for `run` mode), the indicator calls a gRPC `Health.Check` against the configured Temporal target with a
short timeout and records the result under a `temporal` detail:

- `reachable` — whether `HealthCheckResponse` came back as `SERVING` inside the timeout.
- `target` — the `temporal.connection-target` value the probe used.
- `configuredTaskQueues` — distinct, sorted task queues registered with `GlobalManager` for `Process`-typed generated
  classes.
- `error` — short failure description; present only when `reachable` is `false`.

The behaviour of `/actuator/health` when Temporal is unreachable is controlled by `cbs.health.temporal.fail-status`:

| Value  | Effect when Temporal is unreachable                                                                 |
|--------|----------------------------------------------------------------------------------------------------|
| `none` | `DslHealthIndicator` returns `UP` with the `temporal` detail flagging `reachable=false`. Default.  |
| `down` | `DslHealthIndicator` returns `DOWN` with the same `temporal` detail. Compose `service_healthy` and Kubernetes readiness probes now gate on the real Temporal state. |

The probe timeout is `cbs.health.temporal.timeout` (default `PT2S`). Example YAML:

```yaml
cbs:
  health:
    temporal:
      fail-status: down
      timeout: PT3S
```

When the application is started without a `WorkflowServiceStubs` bean (preview / explain hosts, or any runtime that omits
the Temporal starter), the `temporal` detail is omitted entirely and the indicator behaves exactly as before.

## Graceful shutdown

The starter ships with `server.shutdown: graceful` (T306). On `SIGTERM` — a `docker compose
restart|stop`, a `kubectl` rollout, a plain `Ctrl-C` — the embedded server stops accepting new
connections immediately and lets in-flight `POST /api/dsl/run|preview|explain` requests finish
instead of resetting them mid-execution. Before this, a restart during a `POST /run` reset the
caller's connection while the workflow may already have started (the run row exists), so a client
retry produced a duplicate run.

Knobs:

| Setting | Default | Purpose |
|---------|---------|---------|
| `server.shutdown` | `graceful` | Drain in-flight requests on shutdown. Set to `immediate` to opt out. |
| `spring.lifecycle.timeout-per-shutdown-phase` | `30s` | Upper bound per shutdown phase, including the web-server drain. Requests still running after this are cut. |
| `stop_grace_period` (compose, `app/compose/app.yml`) | `45s` | How long Docker waits after `SIGTERM` before `SIGKILL`. Must exceed the shutdown-phase timeout plus the worker-drain window. |

Interaction with long-running requests: a run or preview that outlives
`timeout-per-shutdown-phase` is still terminated at shutdown — graceful shutdown bounds the wait,
it does not wait forever. Keep the preview execution timeout on
(`cbs.nova.preview.execution.timeout-ms`, default 20s — T298); a preview with the timeout disabled
(`0`) can hold shutdown open for the full 30s window.

Worker vs HTTP ordering: the Temporal `WorkerFactory` lifecycle runs at
`SmartLifecycle` phase `Integer.MAX_VALUE` (`DslWorkerConfiguration.WorkerFactoryLifecycle`), so
workers stop before the web server's graceful-shutdown phase. The two drains do not block each
other; the sync `POST /run` path submits to the `cbsNovaDslProcessExecutor` (which drains via its
own `WaitForTasksToCompleteOnShutdown`), not to the worker directly, so an in-flight run completes
its dispatch even after the worker factory has stopped.

Manual verification:

```bash
docker compose -f app/compose/app.yml up -d spring-app
# start a slow run in the background (a process that sleeps ~20s)
curl -s -X POST http://localhost:8090/api/dsl/run/SlowProcess -d '{"body":{}}' &
sleep 2
docker compose -f app/compose/app.yml restart spring-app
wait   # the curl returns a normal response, not "connection reset by peer"
```

## Run idempotency

`POST /api/dsl/run/{name}` accepts an optional `Idempotency-Key` header. When present, the same
process name + key pair always maps to the same Temporal workflow, so duplicate submissions return
the existing run instead of launching a new one.

- **Header name:** `Idempotency-Key`
- **Validation:** after `trim()`, the value must be non-blank, 1–200 characters, and match
  `[A-Za-z0-9_.:-]+`. A rejected key returns `400 Bad Request` with code `INVALID_IDEMPOTENCY_KEY`.
- **Derivation:** the run id (and Temporal `workflowId`) is:
  `"idem-" + sha256Hex(name + ":" + key)` truncated to the first 32 hex characters.
- **Replay response:** `200 OK` with response header `Idempotency-Replayed: true` and body
  `{ "runId": "<run-id>", "status": "REPLAYED" }`.
- **Dedup window:** no persistent key store is used; deduplication lasts as long as the Temporal
  namespace retains the workflow execution (workflow retention).

## Correlation id

`POST /api/dsl/run/{name}` accepts an optional `X-Correlation-Id` header. When present, the value
is persisted on the `dsl_runs` row and surfaced on the execution list and detail responses. The
 executions list can be filtered by the stored value via `GET /api/executions?correlationId=...`.

- **Header name:** `X-Correlation-Id`
- **Validation:** after `trim()`, the value must be 1–200 characters and match
  `[A-Za-z0-9_.:/-]+`. A rejected value returns `400 Bad Request` with code `INVALID_CORRELATION_ID`.
- **Caller-supplied only:** the server never generates a correlation id. When the header is
  absent the stored value is `null` and existing behavior is unchanged.

## Run lifecycle

### Stuck-run reconciliation

An optional scheduled job can reconcile `dsl_runs` rows that are stuck in `RUNNING` against their
Temporal workflow execution. It is disabled by default; enable it with:

```yaml
cbs:
  runs:
    reconciliation:
      enabled: true
```

When enabled, the job scans `RUNNING` rows whose `started_at` is older than the configured grace
period (`cbs.runs.reconciliation.grace-period`, default `15m`) every
`cbs.runs.reconciliation.scan-interval` (default `5m`), up to `cbs.runs.reconciliation.batch-size`
rows per pass (default `200`). For each candidate row it calls Temporal's describe API using the
run id as the workflow id and maps the real workflow status to the matching terminal
`dsl_run.status`:

| Temporal status | `dsl_run.status` | Notes |
|-----------------|------------------|-------|
| `COMPLETED` | `COMPLETED` | |
| `FAILED` | `FAILED` | |
| `TIMED_OUT` | `FAILED` | recorded error notes the timeout |
| `CANCELED` | `CANCELLED` | |
| `TERMINATED` | `CANCELLED` | recorded error notes the termination |
| `RUNNING` / `CONTINUED_AS_NEW` / `UNSPECIFIED` | — | left in `RUNNING` |
| workflow not found | `STALE` | the row is genuinely gone from Temporal |

The write uses `updateFinishedIfRunning`, so a concurrent terminal transition from the normal
lifecycle callbacks wins and the reconciliation write is ignored. Any other Temporal exception
(skip, log a warning, and retry on the next scan) does **not** mark the run `STALE` — the existing
healthcheck STALE sweep remains the fallback when Temporal cannot answer. This also means the
feature only activates when a `WorkflowClient` bean is available (Temporal runtime mode).

### Temporal schedules

The runtime can expose Temporal Schedule CRUD for **published** DSL process definitions. The
schedule starts the definition's workflow directly via `ScheduleActionStartWorkflow`, so it does not
need a live API caller to fire.

**Endpoints (backend Spring Boot functional router):**

- `GET /api/dsl/schedules` — list schedules created by this service (ids prefixed with `sched-`).
- `POST /api/dsl/schedules` — create a schedule.
- `DELETE /api/dsl/schedules/{definition}` — delete the schedule for a definition (idempotent).

The BFF exposes the same surface under `/api/v1/dsl/schedules`.

**Create request body:**

```json
{
  "definition": "LoanDisbursement",
  "cron": "0 9 * * *",
  "timezone": "UTC",
  "input": { "amount": 100 },
  "note": "Daily morning run"
}
```

- `definition` is required and must be a published generated process.
- `cron` is required.
- `timezone` defaults to `UTC` and is validated with `ZoneId.of`.
- `input` defaults to an empty object.

**Schedule configuration:**

- Schedule id: `sched-<definition>`.
- Per-fire workflow id is assigned by Temporal (form `<scheduleId>-<scheduled-time>`) because we do
  not pin a fixed workflow id.
- Overlap policy: `SKIP`.
- Catchup window: 1 minute.

**Availability:**

The schedule routes and beans only load when a Temporal `ScheduleClient` bean is present. If
Temporal is not configured the endpoints are not registered and requests to them receive a 404 at
the router level.

**Deferred follow-up:**

Runs launched by a schedule currently execute the workflow directly and are visible in the Temporal
UI, but do **not** yet appear in `/api/executions` (`dsl_runs` history integration). History
attribution with `triggeredBy=schedule` is a planned follow-up.
