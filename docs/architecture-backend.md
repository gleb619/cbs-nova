# Temporal DSL Orchestration Engine — Backend Architecture

This project is a **declarative Java DSL for authoring Temporal workflows and activities** without writing Temporal
boilerplate. Business flows are expressed as small, versioned definitions in a dedicated Gradle module; a custom DSL
compiler turns them into production-ready Temporal classes at build time.

This document focuses on the **backend runtime and starter** shipped through the `T146`–`T219` cycle. It is the
authoritative backend architecture companion to [`architecture.md`](architecture.md) and
[`architecture-ui.md`](architecture-ui.md).

## What the DSL is for

The system gives non-developers and developers a shared, lightweight authoring surface for distributed orchestrations.
It introduces four constructs that all share the same execution contract:

| Construct       | Temporal mapping       | Where it lives                            | Purpose                                                               |
|-----------------|------------------------|-------------------------------------------|-----------------------------------------------------------------------|
| **Process**     | Temporal Workflow      | DSL module (`dsl-examples/src/*.java`)      | Orchestrates a sequence of steps; defines the business flow           |
| **Transaction** | Temporal Activity      | DSL module (`dsl-examples/src/*.java`)      | Executes a single, idempotent, retryable action                       |
| **Function**    | None (local helper)    | DSL module (`dsl-examples/src/*.java`)      | Lightweight reusable logic; no Temporal code is generated             |
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
│                    DSL Module Source Files (`dsl-examples/src/`)              │
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
└──────────────────────────────────────┘  └─────────────────────────────────────────┘
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

## Local Temporal runtime (docker-compose)

The full local stack lives in `app/docker-compose.yml`, which `include`s one file per bounded
domain from `app/compose/`. The Temporal slice is `app/compose/orchestration.yml`:

- `temporal` — the Temporal server (auto-setup image) on the compose network, gRPC port `7233`.
- `temporal-ui` — the Temporal Web UI at **http://localhost:8233**.
- `app` (the Spring Boot host, see "App deployment topology" below) — configured with
  `TEMPORAL_ADDRESS=temporal:7233`, mapped through
  `temporal.connection-target=${TEMPORAL_ADDRESS:127.0.0.1:7233}` in `application.yml`.

Workers and the `WorkflowClient` connect to `temporal:7233` inside the compose network, while
local runs without compose fall back to the default `127.0.0.1:7233`. The full domain split
(postgres, auth, error-tracking, app, gitea, observability) is documented in `app/compose/README.md`
and the section below.

## App deployment topology

The `app/` tree is the **publishable application** that consumes the backend modules. It mirrors
the shape an external customer would have: a Spring Boot host, a DSL module that they own and
publish, and a Nuxt host for the admin UI plugin. The backend modules under `backend/` are the
platform; `app/` is the deployable.

### `app/server` — Spring Boot runtime host

`app/server` is the Spring Boot process that actually runs the published DSLs. It is a thin
consumer of `cbs.nova:starter` and `cbs.nova:app-dsl`, both resolved from Maven Local:

- `cbs.nova:starter` (from `backend/starter`) provides the Spring Boot autoconfiguration, the
  `DslRuntime` implementation, the REST controllers, the workers, the run repository, and all
  the runtime infrastructure described earlier in this document.
- `cbs.nova:app-dsl` (from `app/dsl`, see below) is the published artifact containing the
  customer's `DslDefinitionProvider` implementations plus the generated Temporal classes.

`app/server` connects the two halves through **SPI + an `ApplicationRunner`** rather than the
direct in-process registration `backend/starter`'s own integration tests use:

- `app/server/src/main/java/cbs/nova/server/config/DslDefinitionLoaderConfig.java` registers an
  `ApplicationRunner` bean that calls
  `new ServiceLoaderDslDefinitionLoader().load(GlobalManager.globalManager())` at startup. The
  loader uses `java.util.ServiceLoader` to discover every `DslDefinitionProvider` declared in
  `META-INF/services/` on the classpath — i.e. the ones generated by `app/dsl`'s `compileDsl`
  task — and registers them into the shared `GlobalManager`.
- `app/server/src/main/java/cbs/nova/server/config/HelperRegistrationConfig.java` does the same
  for helpers: an `ApplicationRunner` calls `GlobalManager.globalManager().registerHelper("greeter", greeterHelper)`,
  wiring the @`-managed` `GreeterHelper` bean into the DSL runtime alongside the SPI-loaded
  definitions.

This is the deployment boundary the rest of the runtime is built around. There is no separate
`admin` service, no per-DSL executable — one Spring Boot process hosts everything published to
it via SPI.

### `app/dsl` — standalone DSL module (build → publish flow)

`app/dsl` is what an external team's repo would look like. It applies the published
`cbs.nova.dsl` Gradle plugin (from `backend/dsl-gradle-plugin/`), authors compact DSL sources
in `src/dsl/` and compact `@Json` records in `src/models/`, and produces a single jar that
`app/server` (or any other consumer) can depend on.

The build pipeline:

1. The DSL Gradle plugin's `compileDsl` task
   (`backend/dsl-gradle-plugin/src/main/java/cbs/nova/dsl/gradle/DslCompilerPlugin.java`)
   scans the compact sources, builds the descriptor AST, and generates the same Temporal
   workflow/activity interfaces and definitions that `dsl-examples` produces, plus the
   `META-INF/services/cbs.nova.dsl.DslDefinitionProvider` SPI registration file.
2. `copyGeneratedClasses` copies the generated classes into the main compile output so they
   are packaged in the published jar.
3. `./gradlew publishToMavenLocal` publishes `cbs.nova:app-dsl:0.0.1-SNAPSHOT` to `~/.m2/`.
4. `app/server` resolves that artifact at build time (via `mavenLocal()` in `app/server/build.gradle`)
   and exposes its definitions at runtime through the SPI loader described above.

The plugin is configured with `runtimeModule = ''` so the DSL compiler classpath resolves only
`dsl-api`, `dsl`, and `dsl-codegen` from Maven Local — it does not pull the full Spring Boot
runtime through `starter`. That isolation is what lets `app/dsl` be a lean, customer-shaped
module rather than a fork of the backend.

Full build → run sequence:

```bash
cd backend
./gradlew -p dsl-platform publishToMavenLocal
./gradlew -p dsl-plugins  publishToMavenLocal
./gradlew -p dsl-starter  publishToMavenLocal -x test
cd ../app/dsl && ./gradlew publishToMavenLocal
cd ../app/server && ./gradlew build && ./gradlew bootRun
```

### Compose topology

`app/docker-compose.yml` is the top-level manifest. It `include`s one file per bounded domain
from `app/compose/`, so each domain keeps its own service definitions, ports, and env wiring:

| Include file               | Purpose                                                      |
|----------------------------|--------------------------------------------------------------|
| `postgres.yml`             | Shared Postgres instance provisioning per-domain DBs         |
| `auth.yml`                 | Keycloak identity provider (uses shared Postgres)            |
| `error-tracking.yml`       | Bugsink error tracker (uses shared Postgres)                 |
| `orchestration.yml`        | Temporal server + UI (uses shared Postgres)                  |
| `app.yml`                  | CBS Nova Spring Boot application (`app/server`)              |
| `gitea.yml`                | Gitea git hosting (uses shared Postgres)                     |
| `observability.yml`        | Grafana, Loki, Jaeger, Prometheus, OpenTelemetry Collector   |

The shared Postgres model is the important detail: there is **one** Postgres container, and
`app/compose/postgres-initdb.d/01-create-dbs.sh` creates the four per-domain databases
(`keycloak`, `gitea`, `bugsink`, `temporal`) plus their roles on first boot. The credentials
default to the historical per-service values (`keycloak`, `gitea`, `bugsink`, `temporal`) and
can be overridden via the matching `KEYCLOAK_DB_PASSWORD` / `GITEA_DB_PASSWORD` /
`BUGSINK_DB_PASSWORD` / `TEMPORAL_DB_PASSWORD` env vars on the `postgres` service. The data
volume is named `postgres-data`; deleting it re-runs the init script on next start.

The Spring Boot app is pre-configured with OTLP env vars (`MANAGEMENT_OTLP_TRACING_ENDPOINT`,
`MANAGEMENT_OTLP_LOGS_ENDPOINT`) targeting the OpenTelemetry Collector. Traces flow to Jaeger,
logs to Loki/Grafana, and metrics to Prometheus via Micrometer. The Compose README enumerates
all ports and env vars; the ones that matter for the topology are port `8090` (app),
`8080` (Keycloak), `8000` (Bugsink), `8233` (Temporal UI), `3000` (Grafana),
`16686` (Jaeger), `9090` (Prometheus), `3001` (Gitea), `5432` (Postgres).

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                           Docker Compose Network (app/)                              │
│                                                                                      │
│  ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌─────────────────────────────┐  │
│  │  Keycloak  │   │   Gitea    │   │  Bugsink   │   │   Temporal + temporal-ui    │  │
│  │ (auth.yml) │   │ (gitea.yml)│   │(error-     │   │   (orchestration.yml)       │  │
│  │  :8080     │   │   :3001    │   │tracking.yml)│   │   :7233, :8233              │  │
│  │            │   │            │   │   :8000     │   │                             │  │
│  └─────┬──────┘   └─────┬──────┘   └─────┬──────┘   └──────────────┬──────────────┘  │
│        │                │                │                          │               │
│        │                │                │                          │               │
│  ┌─────┴────────────────┴────────────────┴──────────────────────────┴──────────────┐  │
│  │                  Shared Postgres (compose/postgres.yml)  :5432                 │  │
│  │  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐                     │  │
│  │  │ keycloak │   │  gitea   │   │ bugsink  │   │ temporal │   (per-domain DBs   │  │
│  │  │   DB     │   │   DB     │   │   DB     │   │   DB     │    created by        │  │
│  │  └──────────┘   └──────────┘   └──────────┘   └──────────┘    01-create-dbs.sh)│  │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
│  ┌──────────────────────────────────────────┐  ┌───────────────────────────────────┐ │
│  │   app/server (compose/app.yml)           │  │  Observability (observability.yml)│ │
│  │   Spring Boot host, port 8090           │  │                                   │ │
│  │   SPI loads cbs.nova:app-dsl            │  │  ┌──────────┐  ┌──────────────┐  │ │
│  │   AppRunner registers greeter helper    │  │  │  OTel    │  │   Jaeger     │  │ │
│  │   Consumer of cbs.nova:starter          │  │  │Collector │  │   :16686     │  │ │
│  │   OTLP exports → OTel Collector         │  │  │  :4318   │  └──────────────┘  │ │
│  │                                          │  │  └─────┬────┘                    │ │
│  │                                          │  │        │                         │ │
│  │                                          │  │  ┌─────┴────┐  ┌──────────────┐  │ │
│  │                                          │  │  │  Loki    │  │  Prometheus  │  │ │
│  │                                          │  │  │  :3100   │  │   :9090      │  │ │
│  │                                          │  │  └──────────┘  └──────────────┘  │ │
│  │                                          │  │  ┌──────────┐                    │ │
│  │                                          │  │  │ Grafana  │ (queries Loki +    │ │
│  │                                          │  │  │  :3000   │  Prometheus)        │ │
│  │                                          │  │  └──────────┘                    │ │
│  └────────────────────┬─────────────────────┘  └───────────────────────────────────┘ │
│                       │                                                               │
│                       │ HTTP (BFF → Spring Boot)                                      │
│                       ▼                                                               │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐ │
│  │   app/ui (@cbs/operator-portal) — host Nuxt app, port 3000                       │ │
│  │   mounts @cbs/admin-ui-plugin under /nova-admin                                   │ │
│  │   Nitro BFF /api/v1/** → http://app:8090 (via Spring Boot REST surface)          │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

The `app/ui` outline above is the user-facing entry point; its internals (host-app wiring,
local-registry tarball flow, plugin mount path) are described in
[`architecture-ui.md`](architecture-ui.md#operator-portal-host-app-appui).

## Runtime layers

The runtime is deliberately layered so generated code has a single entry point:

- **Registry layer** — `ProcessRegistry`, `TransactionRegistry`, and `HelperRegistry` store definitions by name.
  Functions declared in DSL sources are registered in `HelperRegistry` alongside normal `@Helper` classes.
- **Runner layer** — `ProcessRunner`, `TransactionRunner`, and `HelperRunner` execute definitions against typed
  `Context`s.
- **Manager layer** — `GlobalManager.getInstance()` is the only facade generated code talks to. It delegates internally
  to `ProcessManager`, `TransactionManager`, and `HelperManager`.

See [Runtime details](dsl/runtime.md) for the full contract, operational modes, and REST endpoints.

## Input conversion (`T205`)

`starter.converter.MapInputConverter` converts ad-hoc map/JSON inputs into typed process and helper
arguments. `T205` moved it from the `dsl` module into `starter` and rewrote it to convert records through
cached Avaje Jsonb adapters (`adapterCache` in a `ConcurrentHashMap`), falling back to a Jackson
`ObjectMapper` for non-`@Json` targets. The converter handles primitives, enums, arrays, collections,
records, and generic types without reflection on the hot path.

## Preview, Explain, and Run pipelines (`DevDslRuntime`, `T187`)

The `cbs-nova-starter` module provides a Spring-backed implementation of the `DslRuntime` interface:
`cbs.nova.starter.DevDslRuntime`. It is now a thin dispatcher — `preview()`, `run()`, and `explain()` each delegate
to a dedicated pipe (`PreviewDslPipe`, `RunDslPipe`, `ExplainDslPipe`).

`T187` replaced the earlier direct-field-access/hardcoded-dispatch implementation (and the leak-prone
`runId`-keyed singleton collectors it relied on) with a proper middleware chain: `DslExecutionPipeline` runs an
ordered list of `DslPipeStage`s (a `(context, next) -> Result<?>` SPI, assembled per pipe via a `Builder`) around a
terminal `DispatchStage`. Each stage owns per-run, per-instance state (e.g. `ExecutionTreeStage`, `MetricsStage`,
`DryRunLogStage`, `FakingStage`) and is composed declaratively rather than called by name from a single class. A
separate `HelperInterceptor` SPI (see "Fake configuration" below) lets a stage short-circuit an individual helper
call from inside `DispatchStage` without every pipe needing to know about it. The `ExternalCallListener` interface
still exists and is still used only for optional side notifications, not report building.

### Call capture for external calls (`T146`–`T158`)

Preview/Explain reports surface every external call the DSL makes so authors can see side effects without running them.
Capture is implemented by interceptors in the starter:

| Kind            | Interceptor class                                                                                                    | What it records                              |
|-----------------|----------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| **JDBC / DB**   | `capture.DataSourceProxyBeanPostProcessor` → `RecordingDataSource` → `RecordingConnection` → `RecordingStatement` / `RecordingPreparedStatement` / `RecordingCallableStatement` | `executeQuery`/`executeUpdate`/`execute`/`executeBatch` calls, SQL target, first SQL token as operation |
| **HTTP / Feign** | `capture.ExternalCallFeignInterceptor`                                                                               | Feign request method + URL                   |
| **Temporal Activity** | `preview.TemporalActivityCallCaptureInterceptor` (wraps `TransactionInvoker`)                                            | Each transaction invocation as `activity`    |
| **Messaging (Kafka)** | `preview.MessagingCallCaptureProducerFactoryBeanPostProcessor` → `MessagingCallCaptureProducerFactory` → `MessagingCallCaptureProducer` | Kafka `Producer.send` calls as `mq`          |

All recorded calls are normalized by `ExternalCallRecorder` (implemented by `RunScopedExternalCallRecorder`, renamed
from the earlier `ExternalCallTracker`) into a canonical type (`database`, `http`, `mq`, `filesystem`, `external_api`,
`microservice`, `activity`, `other`) and stored in a `ThreadLocal` list for the current preview run. The recorder's
`startRun`/`finishRun` are called around the pipeline by `ExternalCallRecordingStage`. It also keeps global counts,
but those are reset explicitly and are mostly used for diagnostics.

### Fake configuration (`T198`)

Per-request mocking was removed entirely: `DslRuntimeResource.DslRequest` is now just `(body, metadata)` — there is no
`mocks` field, and `ExternalCallTracker.startMocking()` no longer exists. "Mocks" were renamed to **fakes**, and
faking is now driven exclusively by **startup-only YAML** under `cbs.nova.fakes` (bound to `CbsNovaFakesProperties`,
a `FakeConfig` of `(type, code, response)` entries).

Faking happens at the **helper-execution boundary**, not per external-call-kind:

- `FakingStage` (a `DslPipeStage` wired into the `Run`, `Preview`, and `Explain` pipes alike) registers the
  configured `FakeConfig` for the current `runId` in `RunScopedFakeConfig` before the DSL executes, and removes it in
  a `finally` block so it never leaks across runs.
- `FakeHelperInterceptor` implements the `HelperInterceptor` SPI. Before a helper or function actually runs, it looks
  up a configured response by `helper`/`function` type + name in the run-scoped `FakeConfig`; if found, it returns
  that response directly instead of invoking the real helper.

Because faking short-circuits at the helper boundary — not the DB/HTTP/Activity/MQ call-capture interceptors described
above — there is no longer a DB/HTTP-not-short-circuited limitation: a faked helper never reaches its underlying
JDBC/Feign/Activity/MQ call at all. Fakes apply uniformly across Run, Preview, and Explain, since the same
`FakingStage`/`FakeHelperInterceptor` pair is wired into all three pipes.

`T202` removed the now-dead `WhatIfConfigPanel` from the frontend runner UI — the panel built a per-request `mocks`
payload that the backend no longer accepts. Fakes are configured by operators via `application.yml`, not per-request
from the UI.

### Runtime call-tree + depth/cycle guard (`T148`/`T155`)

`ExecutionTreeStage` (a `DslPipeStage`, skipped in `RUN` mode) creates a fresh `ExecutionTreeCollector` per run, seeded
with `cbs.nova.preview.callTree.maxDepth:32`. The collector builds a tree of `CallNode`s as the DSL dispatches processes,
transactions, helpers, and functions. When the depth limit is exceeded, the collector emits a `<truncated>` sentinel
so the UI can render the boundary instead of hanging or blowing the stack. The call tree is returned in the
`PreviewReport` and also consumed by `PreviewMetricsCollector` to count `CallKind`s.

### Dry-run logging (`T149`/`T173`/`T197`)

Logback is wired through a `DryRunLogbackAppender` named `DRY_RUN`. The appender only records events while a
dry-run context is active, routing them into a per-run `DryRunLogBuffer`.

`T197` removed the `ScopedValue`-based `ScopedValueDryRunLoggingContext` entirely. Log accumulation is now owned by
the DSL pipe itself: `DryRunLogStage` (a `DslPipeStage`, skipped in `RUN` mode) creates a fresh `DryRunLogBuffer` per
run, registers it under the `runId` in `DryRunLogBufferRegistry` so the appender can route events into it, sets the
`runId` on the `DryRunLoggingContext` for the duration of the run, and — in a `finally` block — drains the buffer into
the pipe context, removes it from the registry, and clears the context. This avoids the leak-prone pattern of a
long-lived `runId`-keyed map that only the DSL author remembers to clean up. The `runId` is still restored on Temporal
worker nodes via `DryRunLoggingContextPropagator`.

A `PreviewReport` includes the drained log lines as a list of formatted strings, and `ExplainReport` includes the typed
log events as maps (`level`, `message`, `timestamp`, `mdc`, `runId`). The `runId` is also placed in SLF4J MDC during
the run, so any normal log line emitted during preview is correlated with the run.

### Preview metrics (`T162`)

`PreviewMetricsCollector` is a `ThreadLocal` collector started at the beginning of a preview run and stopped at the end.
It records:

- `executionDurationMs` — wall-clock time of the run.
- `memoryUsedBytes` — heap delta from start to finish.
- `callCounts` — counts by `CallKind` (`PROCESS`, `TRANSACTION`, `HELPER`, `FUNCTION`) from the call tree.
- `externalCallCounts` — counts by normalized external-call type from `ExternalCallRecorder`.

The latest snapshot is kept in a static field and published as Micrometer gauges via
`PreviewMetricsAutoConfiguration`: `cbs.nova.preview.execution.duration`,
`cbs.nova.preview.memory.used`. The snapshot is returned in the `PreviewReport`/`ExplainReport`.

### Preview result caching (`T164`)

`PreviewResultCache` is a TTL-backed, in-memory cache keyed by `PreviewCacheKey` (entity name + DSL descriptor hash +
input hash). It is enabled by default (`cbs.nova.preview.cache.enabled:true`) and uses a TTL of 5 minutes by default
(`cbs.nova.preview.cache.ttlMs:300000`).

- Cache hits/misses are exposed as Micrometer gauges `cbs.nova.preview.cache.hit.count` and
  `cbs.nova.preview.cache.miss.count`.
- `invalidateByDslHash()` can be called to drop every cached entry for a given DSL descriptor hash.
- `PreviewCacheStage` wraps the pipeline; when a hit is found, the cached `PreviewReport` is returned immediately
  without executing the rest of the pipeline (including the DSL run itself).

### Preview error handling (`T163`)

`PreviewErrorHandler` maps exceptions thrown during preview/explain into a structured `PreviewErrorDetail`:

| Code                         | Trigger                                                                   |
|------------------------------|---------------------------------------------------------------------------|
| `DSL_COMPILATION_ERROR`      | `DslValidationException`                                                  |
| `HELPER_NOT_FOUND`           | Unknown DSL entity, helper lookup failure, or missing bean                |
| `EXTERNAL_CALL_FAILED`       | `SQLException` during preview                                             |
| `INPUT_VALIDATION_ERROR`     | `ClassCastException` or `IllegalArgumentException`                        |
| `COMPENSATION_ERROR`         | `DslCompensationException`                                                |
| `TIMEOUT_EXCEEDED`           | `TimeoutException`                                                        |
| `UNKNOWN_ERROR`              | Everything else                                                           |

Each error carries a `message`, `suggestion`, and a JSON-serializable `context` map. The `DslRuntimeResource` turns
the first preview error into an HTTP 422 `ErrorResponse` with a generated `exceptionId`.

### Known blocked / not-shipped items

- **Preview execution sandboxing (`T165`)** — not shipped. `SecurityManager`-based sandboxing is infeasible on the
  repo's JDK 25 because JEP 486 permanently disabled `SecurityManager` enforcement in JDK 24+. Any documentation that
claims this exists would be wrong; it is a known gap requiring a human decision on an alternative isolation approach.
- **Preview/Explain report supertype (`T168`)** — still not shipped. `T187` did land a general-purpose `DslPipeStage`
  middleware SPI (see "Preview, Explain, and Run pipelines" above) that subsumes most of what T168's listener
  architecture would have provided — metrics, dry-run logging, call-tree capture, caching, and fakes are all
  independent, composable stages today, not hardcoded field access. What T168 originally asked for and still does not
  exist is a common `Report` supertype: `PreviewReport` and `ExplainReport` remain separate, unrelated types, so a
  consumer cannot treat the two report shapes polymorphically. T168 stays blocked pending a replanning pass to decide
  whether a report supertype is still worth introducing given the pipe-stage SPI now largely does the same job.

## Run persistence (`T176`)

Live runs are persisted through `DslRunRepository` so the UI and operators can inspect execution history. The starter
ships a JDBC-backed implementation plus an in-memory fallback.

### `DslRunEntity` schema

`persistence.DslRunEntity` maps to the `dsl_runs` table (configurable schema/table name via
`DslRunPersistenceProperties`):

| Column              | Purpose                                               |
|---------------------|-------------------------------------------------------|
| `id`                | Surrogate key                                         |
| `run_id`            | Public correlation id (also Temporal workflow id)   |
| `process_name`      | Name of the DSL process                               |
| `status`            | `RUNNING`, `COMPLETED`, `FAILED`, `STALE`, ...        |
| `input_json`        | Serialized input                                      |
| `output_json`       | Serialized output (or `{}` while running)             |
| `error_message`     | Error text when failed                                |
| `context_json`      | Serialized trace/AST/call-tree context                |
| `started_at`        | Start instant                                         |
| `finished_at`       | Finish instant                                         |
| `execution_mode`    | `RUN`, `PREVIEW`, or `EXPLAIN`                        |

### Application-level field encryption

`persistence.AesFieldEncryptor` provides AES-256/GCM encryption for `input_json`, `output_json`, and `context_json`. The
key is configured through `cbs.nova.persistence.run.encryption.key` (hashed with SHA-256 to derive a 32-byte key). When
encryption is disabled, `NoOpFieldEncryptor` passes strings through unchanged. Encryption/decryption is applied inside
`JdbcDslRunRepository` before/after the MapStruct entity mapping.

### MapStruct conversion

`persistence.DslRunMapper` maps between the domain `DslRun` record and `DslRunEntity`. The repository owns the encryption
calls around the mapper so that the mapper stays a pure shape converter.

### Transaction execution history (`T210`/`T211`)

Each successful transaction execution is persisted separately through `history.TransactionExecutionRepository`
(`dsl-api`) so that run diagnostics can show the exact transaction lineage. The starter provides two implementations:

- `repository.InMemoryTransactionExecutionRepository` — lock-free in-memory store using `ConcurrentHashMap` and
  `CopyOnWriteArrayList`; used when no JDBC datasource is configured.
- `persistence.JdbcTransactionExecutionRepository` — JDBC/Flyway implementation backed by the `dsl_run_transactions`
  table (migration `V3__create_dsl_run_transactions.sql`).

`DefaultExecutionListener` now delegates to `TransactionExecutionRepository.save(...)`;
transaction success history is persisted via the repository.

## Runtime concurrency cleanup (`T209`/`T212`/`T214`/`T215`/`T216`/`T219`)

A sweep of runtime state holders replaced `synchronized` blocks and double-checked locking with lock-free or
concurrent primitives:

- `GlobalManager` — instance management uses an `AtomicReference` with `compareAndSet` instead of double-checked
  locking (`T214`/`T215`).
- `InMemoryDslRunRepository` — uses `ConcurrentHashMap` + `ConcurrentLinkedDeque` with a bounded capacity (`T209`).
- `InMemoryTransactionExecutionRepository` — uses `ConcurrentHashMap` + `CopyOnWriteArrayList` (`T219`).
- `ExecutionTraceCollector` — uses `ConcurrentLinkedQueue` + `AtomicBoolean` (`T216`).
- `DryRunLogBuffer` / `DryRunLogBufferRegistry` — per-run buffers and registry use concurrent queues and
  `ConcurrentHashMap` (`T212`).
- `ExecutionTreeCollector` — per-run instance (no cross-run shared mutable state); frame stack is local to the run.

This removes the earlier leak-prone `synchronized`/singleton patterns without changing per-run semantics.

## Observability (`T175`)

### Sentry

`controllers.DslExceptionHandler` captures `DslException` and generic `Exception` instances to Sentry, attaching the
`runId` as a Sentry tag when available. The handler maps DSL failures to HTTP 422 `ErrorResponse` bodies and unknown
failures to HTTP 500. Sentry is treated as optional: every call is guarded so an unconfigured SDK does not break the
application.

### OpenTelemetry and MDC propagation

`runId` is the primary correlation key. It is placed into:

- SLF4J MDC (`runId`).
- Sentry tags (`runId`).
- OpenTelemetry baggage and span attributes (`runId`).

`TemporalConfiguration.cbsNovaDslContextDecorator()` is a Spring `TaskDecorator` that copies the submitting thread's
MDC into the worker thread of the custom `cbsNovaDslProcessExecutor`. The same key is propagated across Temporal nodes
via the `DryRunLoggingContextPropagator` so that dry-run logs from a worker are still attributed to the originating run.

## Async process service (`T170`)

`services.TemporalDslProcessService` is the non-blocking entry point for live process execution.

- `startProcess(name, input, metadata)` returns a `ProcessRun` handle immediately: a generated `runId` and a
  `CompletableFuture<Result<?>>` that completes once the workflow finishes and the repository is updated.
- A configurable `Clock` is used for `startedAt` / staleness checks; it is mutable for tests.
- A dedicated `ThreadPoolTaskExecutor` (`cbsNovaDslProcessExecutor`) handles async DB writes and workflow supervision,
  bounded and named so the Spring lifecycle owns it.
- A separate single-thread `ScheduledExecutorService` (`cbsNovaDslProcessHealthcheckExecutor`) runs the healthcheck.
- DB writes are async by default (`cbs.nova.process.async-db-save:true`) and failures are logged but do not poison the
  workflow outcome.

### STALE status

The healthcheck thread scans known process names for runs that are still `RUNNING` after a staleness threshold
(`cbs.nova.process.healthcheck.stale-threshold:PT5M`, checked every
`cbs.nova.process.healthcheck.interval:PT30S`). Runs exceeding the threshold are marked `STALE` with a synthetic error
message. `NOT_FINISHED_AT` is used as a sentinel `finishedAt` value while a run is in flight, keeping the column
non-null for serialization safety.

## Temporal service lifecycle (`T169`/`T213`)

The Temporal client and worker are Spring-managed beans in `TemporalConfiguration` and `DslWorkerConfiguration`:

- `WorkflowServiceStubs` and `WorkflowClient` are `@ConditionalOnMissingBean` beans.
- `TemporalDslProcessLauncher` and `TemporalTransactionInvoker` are exposed as beans and registered into the DSL config
  at startup.
- `TemporalConfiguration` exposes a `WorkerFactory` bean (with `destroyMethod="shutdown"`) and an
  `ApplicationRunner` (`temporalWorkerRegistrationRunner`) that registers generated workflow implementations per
  task queue and starts the factory when `dsl.worker.enabled=true`. `T213` moved this registration from lazy
  double-checked-locking inside `services.TemporalDslService` to eager startup via Spring Boot's `ApplicationRunner`
  SPI.
- `DslWorkerConfiguration` still creates the default task-queue `Worker`, registers generated activity implementations
  via the `GeneratedClassProvider` SPI, and uses a `SmartLifecycle` adapter to start/stop its factory with the Spring
  context.
- Generated workflow and activity implementations are discovered via the `GeneratedClassProvider` SPI and registered
  without hardcoded class references.

`services.TemporalTransactionInvoker` is the bridge from a DSL transaction to a Temporal Activity stub. If the
transaction or generated class is missing, it falls back to local execution and logs a warning. Activity options are
built from the DSL transaction's retry policy and timeouts, defaulting to `DslConfig.defaultRetryPolicy()` when no
policy is set.

## Serialization unification (`T171`)

The original goal of a codegen-generated "models converter" bridging Avaje JSON-B and Jackson was dropped because
cleanup work in `T167`, `T169`, and `T170` removed every other ad-hoc conversion site. The only concrete shipped change
is that `helpers.JsonExtractHelper` now accepts an injected `ObjectMapper` constructor parameter, matching the pattern
already used by `HttpCallHelper`. The backend still uses Jackson for runtime JSON and Avaje JSON-B annotation processing
per record; there is no single unified converter class.

## JSON-native DSL access (`T194`)

`Context` exposes JSON access natively: `Context#json()`/`asJsonValue()` return the request body as a `JsonValue`
(a `dsl-api` interface with a Jackson-backed implementation), and `.json(value)`/`asJsonValue(value)` wrap any object
the same way. The expression evaluator supports `.json()` path navigation directly on interpolated values.
`helpers.JsonExtractHelper` (see "Serialization unification (`T171`)" above) is now a **deprecated facade** over this
native engine, retained only for backward compatibility with existing DSL flows — new flows should use
`Context`/expression JSON access directly instead of the helper.

## Spring Boot autoconfiguration & starter packaging (`T174`)

`config.DslRootAutoConfiguration` is the single autoconfiguration entry point listed in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It `@Import`s all other starter
autoconfigs so host applications only see one root import:

- `DryRunLoggingAutoConfiguration`
- `DslRunRepositoryConfiguration`
- `TemporalConfiguration`
- `PropertyResolverConfiguration`
- `DslAutoConfiguration`
- `DslWorkerConfiguration`
- `DataSourceCallAutoConfiguration`
- `FeignCallAutoConfiguration`
- `PreviewAutoConfiguration`
- `PreviewCacheAutoConfiguration`
- `MessagingCallCaptureAutoConfiguration`
- `PreviewMetricsAutoConfiguration`
- `DslReloadRouterConfiguration`

`OpenApiConfig` sources the OpenAPI version from Spring Boot `BuildProperties` (produced by the `buildInfo()` Gradle
block), falling back to `0.0.1-SNAPSHOT` when build info is absent.

## DSL reload endpoint (`T174`/`T175`)

`POST /api/dsl/reload` is registered as a functional `RouterFunction` by `DslReloadRouterConfiguration` and gated by
`dsl.reload.enabled` (on by default). `controllers.DslReloadResource` compiles sources from `dsl.source-dir` with the
system Java compiler, builds a dedicated `URLClassLoader`, and reloads definitions via `DslDefinitionLoader` and the
`DslDefinitionProvider` / `HelperResolver` SPIs.

`T207` split the former monolithic `DefinitionLoader` into a `DslDefinitionLoader` interface with two
implementations:
- `ServiceLoaderDslDefinitionLoader` — non-reflective; loads `DslDefinitionProvider`s from the classpath via
  `java.util.ServiceLoader`.
- `CompilingDslDefinitionLoader` — reflective; used by `DslReloadResource` to compile compact DSL sources on the fly
  and instantiate them through a dedicated `URLClassLoader`.

Compact-source files implementing `DslCompactSource` are also rescanned as a fallback. The global manager is reset
before reload to avoid stale definitions.

## DSL introspection (`T175`/`T177`/`T182`)

`controllers.DslIntrospectionResource` exposes read-only metadata about registered DSL entities:

- `GET /api/dsl/processes` and `/api/dsl/processes/{name}`
- `GET /api/dsl/transactions` and `/api/dsl/transactions/{name}`
- `GET /api/dsl/helpers`
- `GET /api/dsl/helpers/search?name=&type=&description=` — search across processes, transactions, helpers, and
  functions (`T177`).
- `GET /api/dsl/definitions` — aggregate list of every registered definition with name, type, and optional input schema
  (`T182`).

Input schemas are generated by `JsonSchemaGenerator`, an interface with a Jackson 3 implementation bean
(`dsl.jsonschema.JacksonJsonSchemaGenerator`, `T208`). It builds schemas through Jackson's
`JsonFormatVisitorWrapper` SPI rather than hand-written field inspection, and `T208` removed the third-party
schema-library dependency entirely. For compact-source parameters, the generator produces a schema from the declared parameter descriptors.

## Actuator endpoints

The `cbs-nova-starter` module ships Spring Boot Actuator on the runtime classpath and exposes a
small set of operational endpoints over HTTP. They are configured in
`backend/starter/src/main/resources/application.yml`:

| Endpoint                | Purpose                                                            |
|-------------------------|-------------------------------------------------------------------|
| `GET /actuator/health`  | Aggregated health. Includes a `dsl` component (from `DslHealthIndicator`) reporting `processes`/`transactions`/`helpers` counts out of `GlobalManager`. `show-details: always` so individual components are visible. |
| `GET /actuator/info`    | Build metadata (group, artifact, version, build time) via the Gradle `springBoot { buildInfo() }` block, plus `env`/`java` info contributors. |
| `GET /actuator/metrics` | Micrometer metrics list (e.g. `jvm.memory.used`); individual metrics are readable at `/actuator/metrics/{name}`. |

> **Auth status:** these endpoints are **unauthenticated** today. There is no Spring Security / Keycloak filter
> chain anywhere in `backend/starter` yet, so nothing gates access to `/actuator/*`. Any host that can reach
> the servlet context can read health, info, and metrics. Gating (e.g. a `permitAll`/`authenticated` split
> once security is introduced) is deferred until a security filter chain exists in this repo.

## Building the app image

The `spring-app` image is built from the root of the repository using a
multi-stage Dockerfile located at `app/Dockerfile`.

Build the image manually and tag it as `cbs-nova:latest`:

```bash
docker build -t cbs-nova:latest -f app/Dockerfile .
```

`docker compose build` will produce the same tag because the `spring-app`
service keeps `image: cbs-nova:latest` and adds a `build` block that points at
the same Dockerfile:

```bash
docker compose build spring-app
```

When `docker compose up` is run, compose will use the locally available
`cbs-nova:latest` image if it exists, or build it first from the declared
`build` context. The Dockerfile compiles the full backend in a JDK image and
then copies only the `:starter` fat jar into a smaller JRE runtime image.

## See also

- **[DSL constructs & execution contract](dsl/constructs.md)** — `Executable`, `Context`, and the semantics of Process,
  Transaction, Function, and Helper.
- **[Preview mode (dry-run)](dsl/preview-mode.md)** — how preview executes DSL flows locally without Temporal and returns
  a report for coding-agent explainability.
- **[Authoring DSL flows](dsl/authoring.md)** — source files, builder API, `Result` type, helper/transaction calls,
  compensation, and the full loan-disbursement example.
- **[Working with DSL examples](dsl/examples.md)** — source layout, building examples, the Testcontainers integration test, and tips for adding new flows.
- **[Compile-time code generation](dsl/codegen.md)** — generated class naming/versioning, the Gradle module, the
  generation pipeline, and generated code samples.
- **[IDEA plugin for DSL editing](dsl/idea-plugin.md)** — stops IntelliJ IDEA from flagging compact DSL/model sources
  as broken Java, syncs source roots via Gradle Tooling API, and adds a Compile DSL Sources action.

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

## Implementation roadmap (T146–T201)

The roadmap below reflects the kanban state as of the `T220` refresh:

| ID   | Status      | Title                                                        | Notes                                                       |
|:-----|:------------|:-------------------------------------------------------------|:------------------------------------------------------------|
| T146 | Done | Preview/explain external-call capture (DataSource/Feign/Activity/MQ) | JDBC, Feign, Temporal Activity, and Kafka producer capture. |
| T147 | Done | Preview call-tree visualization | Depth-limited runtime AST surfaced in reports and UI. |
| T148 | Done | Preview runtime depth/cycle guard | `ExecutionTreeCollector` with `<truncated>` sentinel. |
| T149 | Done | Dry-run logging capture | `DryRunLogbackAppender` + `DryRunLogEvent` + `runId` tagging. |
| T153 | Done | Call-tree AST panel | Frontend component `CallTreeTab`. |
| T154 | Done | Dry-run logs panel | Frontend component `DryRunLogsTab`. |
| T155 | Done | Call-tree depth/cycle guard backend | `maxDepth` configuration. |
| T156 | Done | Explain diff view | Frontend `ExplainDiffView` using shared `useDiffLines`. |
| T158 | Done | External-call classification/counts | Normalized types and per-run counts. |
| T159 | Done | External-calls panel | Frontend component `ExternalCallsTab`. |
| T160 | Done | What-if mock injection | Activity/MQ fully mocked; DB/HTTP recorded but not applied. |
| T161 | Done | What-if config UI | Frontend component `WhatIfConfigPanel`. |
| T162 | Done | Preview metrics collection + UI | `PreviewMetricsCollector` + `MetricsDiffTable`. |
| T163 | Done | Preview error classification | `PreviewErrorHandler` error codes + suggestions. |
| T164 | Done | Preview result caching | DSL-hash + input keyed cache with Micrometer gauges. |
| T165 | Blocked | Preview execution sandboxing | `SecurityManager` infeasible on JDK 25 (JEP 486). |
| T166 | Done | Preview diff visualization | `PreviewDiffView` + `usePreviewDiff`. |
| T167 | Done | Codegen & DSL internals cleanup | Reflection removal, typed records, DSL config reuse. |
| T168 | Blocked | Preview/Explain listener architecture | Plan stale; metrics/caching/logging layered directly on runtime. |
| T169 | Done | Temporal service lifecycle & transaction invoker cleanup | `@Bean` lifecycle, single `WorkerFactory`, SPI wiring. |
| T170 | Done | Async process-service redesign | Non-blocking `startProcess`, STALE healthcheck, async DB. |
| T171 | Done | JSON / Avaje serialization unification (reduced scope) | `JsonExtractHelper` injected `ObjectMapper`; no new converter. |
| T172 | Done | Helper library hardening | Compensation, unreliable API, Temporal-aware latch/time. |
| T173 | Done | Dry-run logging productionization | `ScopedValue`-style context, Temporal propagation, MDC tagging. |
| T174 | Done | Spring Boot autoconfiguration & starter packaging cleanup | Single root autoconfig, SPI worker wiring, build-info OpenAPI. |
| T175 | Done | REST controllers & observability improvements | Sentry, runId propagation, reload endpoint, introspection. |
| T176 | Done | Run persistence with app-level encryption | `JdbcDslRunRepository`, AES-256/GCM, MapStruct. |
| T177 | Done | Frontend helper-search wiring | `useHelperSearch` + BFF proxy for `/api/dsl/helpers/search`. |
| T178 | Done | JDBC capture invocation-handler tests | Unit coverage for DataSource proxy handlers. |
| T179 | Done | Executions panel component tests | Leaf/presentational component coverage. |
| T180 | Done | Runner panel leaf component tests | `ResultTab`, `ModeSwitcher`, `DiffLine`, `StatusIndicator`, etc. |
| T181 | Done | TemporalTransactionInvoker tests | Unit coverage for fallback and retry-option branches. |
| T182 | Done | Fix definitions introspection wiring | `GET /api/dsl/definitions` aggregator endpoint. |
| T184 | Done | AesFieldEncryptor tests | Unit coverage for the T176 field-encryption path. |
| T185 | Done | SimpleExpressionEvaluator tests | Unit coverage for the sandboxed expression engine behind `Context#eval`. |
| T186 | Done | Surface STALE run status in frontend | `ExecutionStatus`/status badge gained a `Stale` state. |
| T187 | Done | Execution trace/tree collector → pipe-stage scoping | `DslExecutionPipeline`/`DslPipeStage` SPI; per-run collector instances replace leak-prone `runId`-keyed singletons. |
| T188 | Done | JDBC capture: remove reflective dispatch | `DataSourceProxyBeanPostProcessor` chain no longer uses `Method.invoke`. |
| T189 | Done | `SourceCompiler` classpath sourcing | Fixed TODO in `dsl-codegen`'s compact-source compiler. |
| T190 | Done | Typed `@ConfigurationProperties` records | Replaced ad-hoc `@Value`/manual binding across seven starter config sites. |
| T191 | Done | `CompensationRegistry` interface | Extracted interface + `DefaultCompensationRegistry` (lock-free `ConcurrentLinkedDeque`, was `synchronized`). |
| T192 | Done | JSON schema generator library + Spring bean | Library-backed `JsonSchemaGenerator` bean; replaced hand-written static generator (later superseded by Jackson implementation in T208). |
| T193 | Done | DSL package cleanup & dead-code removal | TODO cleanup across `dsl`/`dsl-api`/`dsl-codegen`; `MapInputConverter` became an instance bean. |
| T194 | Done | JSON-native DSL helper | `JsonValue` interface + Jackson impl, `Context.json()`/`.json()` path nav; `JsonExtractHelper` now a deprecated facade. |
| T195 | Done | DSL exception handler SPI | Replaced static dispatch with an overridable interface + impl. |
| T196 | Done | DSL Gradle plugin optional starter module | Decoupled the Gradle plugin from a hard `starter` dependency. |
| T197 | Done | `ScopedValue` dry-run context → pipe stage | Deleted `ScopedValueDryRunLoggingContext`; `DryRunLogStage` owns a per-run `DryRunLogBuffer`. |
| T198 | Done | Two-way fake config, mock → fake rename | Per-request `mocks` removed; startup-only `cbs.nova.fakes` YAML + `HelperInterceptor`/`FakingStage`. |
| T199 | Done | Frontend STALE auto-refresh | `useStalePolling` auto-polls (default 5000ms), pauses when tab hidden. |
| T200 | Done | `DslExecutionsResource` | `GET /api/executions` and `/api/executions/{id}` — Executions page now has a working backend. |
| T201 | Done | Workbench draft autosave | `useWorkbenchDraft` — debounced `localStorage` autosave, 24h TTL, restore banner. |
| T202 | Done | Remove dead What-If Config runner panel | UI cleanup after `T198` removed per-request mocking. |
| T203 | Done | Refresh architecture docs to T201 state | Updated `architecture-backend.md` and `architecture-ui.md` through T201. |
| T205 | Done | Move MapInputConverter to starter with Avaje Jsonb cache | `starter.converter.MapInputConverter`; cached adapters + Jackson fallback; zero reflection. |
| T206 | Done | JDBC capture typed decorators | `RecordingDataSource`/`RecordingConnection`/`RecordingStatement`/`RecordingPreparedStatement`/`RecordingCallableStatement` replace JDK proxies. |
| T207 | Done | DslDefinitionLoader interface split | `ServiceLoaderDslDefinitionLoader` (non-reflective) + `CompilingDslDefinitionLoader` (reflective reload). |
| T208 | Done | Jackson JSON schema generator | `JacksonJsonSchemaGenerator` via Jackson 3 `JsonFormatVisitorWrapper`; third-party schema-library dependency removed. |
| T209/T212/T214/T215/T216 | Done | Runtime lock-free cleanup | Replaced synchronized/DCL in `DryRunLogBuffer`, `ExecutionTreeCollector`, `InMemoryDslRunRepository`, `GlobalManager`, `ExecutionTraceCollector`. |
| T210/T211 | Done | Transaction execution repository | `TransactionExecutionRepository` interface + in-memory and JDBC/Flyway impls; `DefaultExecutionListener` persists via repository. |
| T213 | Done | Temporal worker eager registration | `ApplicationRunner` in `TemporalConfiguration`; `WorkerFactory` bean with `destroyMethod=shutdown`; removed `TemporalDslService` lazy DCL. |
| T217/T218 | Done | Executions offset pagination | `GET /api/executions` `offset` parameter; frontend prev/next page controls via `useExecutionsApi`/`useExecutions`. |
| T219 | Done | Remove synchronized from InMemoryTransactionExecutionRepository | Lock-free `ConcurrentHashMap` + `CopyOnWriteArrayList` implementation. |

T178–T181 and T184–T185 are test-only additions; they increased coverage for the capture handlers, executions panel,
runner panel, `TemporalTransactionInvoker`, `AesFieldEncryptor`, and `SimpleExpressionEvaluator` respectively, but do
not introduce user-facing features.

## Summary

The Temporal DSL Orchestration Engine turns compact Java DSL definitions into durable, observable Temporal workflows.
The T146–T182 cycle added call capture, dry-run observability, preview metrics/caching/error-handling, what-if mocking
(with the DB/HTTP limitation), run persistence with field-level encryption, async process execution with STALE
detection, and a cleaner Spring Boot autoconfiguration model. The following T184–T201 cycle replaced the leak-prone
singleton-collector runtime with a composable `DslPipeStage` pipeline (`T187`), renamed mocks to fakes and moved
faking to startup-only YAML at the helper boundary (`T198`), deleted the `ScopedValue`-based dry-run context in favor
of a pipe-stage-owned log buffer (`T197`), added a native `JsonValue`/`Context.json()` engine that deprecates
`JsonExtractHelper` (`T194`), and wired up the Executions page end-to-end (`T200`) with frontend STALE auto-polling
(`T199`). The T202–T219 cycle removed the dead What-If Config runner panel (`T202`), refreshed this doc set to the
T201 state (`T203`), moved `MapInputConverter` to the starter with cached Avaje Jsonb adapters (`T205`), rewrote
JDBC capture as typed decorators (`T206`), split the DSL definition loader into reflective/non-reflective
implementations (`T207`), replaced the T192 library-backed JSON schema generator with a Jackson 3 implementation (`T208`),
introduced `TransactionExecutionRepository` persistence (`T210`/`T211`), moved Temporal worker registration to a Spring
Boot `ApplicationRunner` (`T213`), switched runtime collectors to lock-free/atomic idioms (`T209`/`T212`/`T214`/`T215`/
`T216`/`T219`), and added offset pagination to the Executions page (`T217`/`T218`). Two items remain blocked
(sandboxing and the preview/explain report-supertype architecture) and are explicitly not documented as implemented.
