# 0004. Preview, dry-run, and Explain execution modes

- **Status:** Accepted
- **Date:** 2026-09-10 (retroactive — records a foundational decision)

## Context

cbs-nova's DSL author defines business flows as small, versioned Processes / Transactions / Functions that compile to
Temporal workflows and activities at build time. Temporal is the production execution engine (see [ADR 0001](0001-temporal-orchestration.md)),
and the surrounding ADRs cover the helper SPI ([ADR 0002](0002-helper-spi.md)) and the admin BFF ([ADR 0003](0003-bff-nitro-admin-ui-plugin.md)).
The engine is also expected to give **business analysts, developers, and CI** a way to validate a DSL definition in seconds —
without standing up a Temporal cluster, without posting to a real downstream system, and without waiting for workflow
scheduling. That requirement surfaced as three distinct audiences:

- **business analysts** iterating on a new flow who need fast, safe "what would this do?" feedback;
- **developers** changing an existing flow who want a quick regression check before they push;
- **CI / coding agents** that need to compile, validate, and exercise definitions without provisioning Temporal services
  in the test environment.

The brainstorm that seeded the design lives in [`docs/ideas/dry-run-preview-explain.ignore.md`](../ideas/dry-run-preview-explain.ignore.md)
and is the non-authoritative reference this ADR supersedes. The end-user-facing howto lives in
[`docs/dsl/preview-mode.md`](../dsl/preview-mode.md); this ADR captures the **why**, the alternatives we rejected, and
the architectural consequences of the seam we chose.

Options considered:

- **A Temporal `TestWorkflowEnvironment` (in-process test server) for every "validate" call** — keeps the same workflow /
  activity path, so behavioural fidelity is high. Costs: spinning up a test server per request is seconds, not milliseconds;
  time-machine and replay semantics still drift from production cluster behaviour; the test server only works inside the
  JVM (no cross-language or out-of-process validation); and signal / query / continue-as-new behaviour is only meaningful
  inside a real workflow, so the test server adds infrastructure cost without closing the fidelity gap for those features.
- **A full mock Temporal cluster that records replays of the deployed workflow** — would be the highest-fidelity option,
  but it is a second large piece of infrastructure to operate (matching the real server's event-history semantics), the
  validation pipeline would now depend on *two* distributed systems, and CI has no way to host it cheaply.
- **Direct in-process execution of the compiled DSL, bypassing the workflow / activity boundary entirely** — runs the same
  `Process` / `Transaction` / `Function` / `Helper` graph the generated Temporal code calls into, but without scheduling a
  workflow. Sub-millisecond feedback, no Temporal dependency, no extra process. Trade-off: nothing about Temporal-specific
  semantics (durable timers, signals / queries, continue-as-new, activity-level retries, heartbeats) is reproduced —
  preview becomes a validation tool, not a proof of the deployed workflow.

## Decision

We will support three execution modes end-to-end, with the same `DslRuntime` facade (`cbs.nova.dsl.DslRuntime`) and the
same `Context` contract in every mode:

- **Run** — `POST /api/dsl/run/{name}` (`DslRuntimeHandler.run` → `DslRuntimeService.run` → `DslRuntime.run`). Executes
  the compiled DSL through `GlobalManager` against a Temporal cluster when one is present and the call originates from
  outside a Temporal workflow thread (see `TemporalDslProcessLauncher.canRun(ctx)`); falls back to direct in-process
  execution otherwise. This is the production path.
- **Preview** (dry-run) — `POST /api/dsl/preview/{name}`. The compiled DSL is executed directly through `GlobalManager`
  with `Context.mode() == PREVIEW`; no Temporal cluster, no Temporal worker, no scheduling. Returns a `PreviewReport`
  with the trace, captured external calls, call-tree AST, and dry-run logs.
- **Explain** — `POST /api/dsl/explain/{name}`. Preview plus a natural-language description and a Mermaid / PlantUML /
  BPMN diagram, rendered by `ExplainDiagramRenderer`.

Mode selection is keyed on `cbs.nova.dsl.ExecutionMode { RUN, PREVIEW, EXPLAIN, COMPENSATION }` and propagated through
`Context.mode()` (see `cbs.nova.dsl.SimpleContext` / `ProcessRichContext`). Each mode is realized as a dedicated
`DslExecutionPipe`:

- `RunDslPipe` — event → trace → fake-config → external-call recording → inline `DispatchStage`.
- `PreviewDslPipe` — `PreviewCacheStage` → `PreviewReportStage` → `MetricsStage` → `ExecutionTreeStage` → `DryRunLogStage`
  → trace → fake-config → external-call recording → bounded `DispatchStage` (timeout via
  `cbs.nova.preview.execution.timeout-ms`, default 20s, dispatched on `cbsNovaPreviewDispatchExecutor`).
- `ExplainDslPipe` — same as Preview but with `ExplainReportStage` (which calls `ExplainDiagramRenderer` for the Mermaid
  diagram) at the head, and no `PreviewCacheStage`.

The single concrete `DslRuntime` implementation is `cbs.nova.starter.DevDslRuntime`; the distinction between "preview" and
"production temporal" lives one layer down inside `DefaultProcessRunner.resolveTemporalLauncher(ctx)`, which returns
`null` for `PREVIEW` / `EXPLAIN` and returns the configured `TemporalProcessLauncher` bean for `RUN` iff
`launcher.canRun(ctx)` succeeds (it requires the caller to be outside a Temporal workflow thread).

### Mechanism

The seams below are the ones that make Preview / Explain real; every name was verified against the tree before writing
this ADR.

**`DslRuntime` and mode selection.** `cbs.nova.dsl.DslRuntime` (`backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/DslRuntime.java`)
exposes `preview`, `run`, and `explain`. The only Spring-wired implementation is `cbs.nova.starter.DevDslRuntime`
(`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/DevDslRuntime.java`), wired in
`TemporalConfiguration.devDslRuntime(...)` with `@ConditionalOnMissingBean`. `DevDslRuntime` holds the three pipes
(`PreviewDslPipe`, `RunDslPipe`, `ExplainDslPipe`) and dispatches each method to the corresponding pipe.

**Three pipes, three stage compositions.** All three pipes live under
`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/pipe/`:

- `RunDslPipe.execute(...)` — `DslExecutionEventStage` → `ExecutionTraceStage` → `FakingStage` → `ExternalCallRecordingStage`
  → `DispatchStage.inline(...)` (no timeout, no preview executor).
- `PreviewDslPipe.execute(...)` — `PreviewCacheStage` → `PreviewReportStage` → `MetricsStage` → `ExecutionTreeStage` →
  `DryRunLogStage` → `ExecutionTraceStage` → `FakingStage` → `ExternalCallRecordingStage` → `DispatchStage(..., timeout,
  executor, meterRegistry, dryRunLoggingContext)`. The bounded timeout is the difference that makes preview "preview"
  rather than "a long synchronous run".
- `ExplainDslPipe.execute(...)` — `ExplainReportStage(diagramRenderer)` → `MetricsStage` → `ExecutionTreeStage` →
  `DryRunLogStage` → `ExecutionTraceStage` → `FakingStage` → `ExternalCallRecordingStage` → `DispatchStage(...)`. Reuses
  the preview stage pipeline; the head stage just wraps the result in `ExplainReport` (with Mermaid via
  `ExplainDiagramRenderer`).

**`DispatchStage` — the seam that threads mode + fake interceptor into the runtime.**
`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/stage/DispatchStage.java` builds a per-execution
`Context` via `ContextFactory.of(body, metadata, mode, runId, ...)` and re-attaches listener, saga, and trace collector
from the incoming context. Critically, **the `HelperInterceptor` is attached to the per-execution `Context` via
`ctx.withHelperInterceptor(helperInterceptor)`** — there is no ThreadLocal, no `GlobalManager` mutation, and no
inherited static state (see the class Javadoc). When a timeout and executor are configured (preview / explain only),
only the actual dispatch call runs on the `cbsNovaPreviewDispatchExecutor` worker; the interceptor travels with the
context the worker receives, so faked helpers still fire on the worker thread. `DispatchStage.dispatch(...)` resolves the
DSL entity name and calls `gm.runProcess(...)` / `gm.runTransaction(...)` / `gm.runHelper(...)`.

**`HelperInterceptor` / `FakeHelperInterceptor`.** The contract
(`backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/helper/HelperInterceptor.java`) is a single-method
`Optional<Result<?>> intercept(String helperName, Context<?> ctx)`. The default implementation, `FakeHelperInterceptor`
(`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/pipe/FakeHelperInterceptor.java`), consults
`RunScopedFakeConfig` (per-run-id fakes from `CbsNovaFakesProperties`) and returns the configured response for matching
`helper` / `function` names, recording the fake as an external call. `HelperManager.executeHelper(...)` and
`executeFunction(...)` read the interceptor off the **current** context (`ctx.helperInterceptor()`) and short-circuit
before the real helper runs. This is the wiring that T417 introduced (the interceptor lives on the per-execution
`Context`, not on `GlobalManager`).

**External-call capture `BeanPostProcessor`s.** Preview and Explain observe — not suppress — outbound side effects; the
captures live on the Spring bean lifecycle so they are invisible to production code:

- `DataSourceProxyBeanPostProcessor` (`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/capture/...`)
  wraps every `javax.sql.DataSource` bean in a `RecordingDataSource` that records `type = "database"` to the
  `ExternalCallRecorder`.
- `MessagingCallCaptureProducerFactoryBeanPostProcessor`
  (`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/preview/...`) wraps every Spring Kafka
  `ProducerFactory` in a `MessagingCallCaptureProducerFactory` that records `type = "mq"`.
- `ExternalCallFeignInterceptor` (registered as a `feign.RequestInterceptor` by `FeignCallConfiguration`, gated by
  `@ConditionalOnClass(name = "feign.RequestInterceptor")`) records `type = "http"` for every Feign call.

`ExternalCallRecordingStage` brackets the pipe with `recorder.startRun(runId)` / `recorder.finishRun(runId)` so each
preview report contains only the calls from its own run; the per-run-id scoping is implemented by
`RunIdKeyedExternalCallRecorder`. The taxonomy constants live on the `ExternalCallRecorder` interface itself
(`TYPE_DATABASE = "database"`, `TYPE_HTTP = "http"`, `TYPE_MQ = "mq"`, `TYPE_FILE_SYSTEM`, `TYPE_EXTERNAL_API`,
`TYPE_MICROSERVICE`, `TYPE_ACTIVITY`, `TYPE_OTHER`) — the three BeanPostProcessors cover the database / mq / http
families; anything that escapes them (`TYPE_OTHER`) is, by definition, not captured.

## Consequences

**Positive**

- Analysts, developers, and CI get millisecond-scale feedback on a DSL change without standing up Temporal. Preview is the
  loop a coding agent uses to validate its own edits before reporting back.
- One `DslRuntime` interface, three pipes, three stage compositions: the seam between preview-only concerns (caching,
  timeouts, diagrams, dry-run logs) and runtime concerns (event bus, fake config, external-call recording) is expressed as
  stage ordering, not as special cases in the runtime itself.
- Side-effect suppression is **layered**: helper short-circuit (`FakeHelperInterceptor`) handles the user-authored
  helpers; outbound I/O (`DataSourceProxyBeanPostProcessor`, the Kafka producer wrapper, the Feign interceptor) is
  recorded, not performed; transactions run locally through `DefaultProcessRunner.runDirectly(...)` instead of being
  scheduled as Temporal activities. A preview report tells you exactly which fakes and external calls fired and with
  what payloads.

**Negative**

- **Preview is a validator, not a proof.** It does **not** reproduce durable timers, signals / queries, continue-as-new,
  per-activity retry policy (with backoff and jitter), heartbeats, or saga compensation as those concepts exist inside
  Temporal. A flow that passes preview can still fail at runtime for reasons only Temporal will see — workflow
  determinism violations, timer-side effects, signal races, non-deterministic activity inputs. The plan brief, the
  preview-mode howto, and ADR 0001 all say so; this ADR pins it down as a first-class consequence.
- **Side-effect suppression is best-effort.** It is bounded by which call families the capture layer intercepts:
  `database` (JDBC `DataSource`), `mq` (Spring Kafka `ProducerFactory`), `http` (Feign `RequestInterceptor`). Anything
  that reaches the network through another path (raw `HttpClient`, a non-Feign `RestTemplate`, a JDBC URL built without
  going through a Spring-managed `DataSource`, an AMQP / JMS client not covered by the Kafka wrapper, direct file I/O,
  shell-out) is **not** captured and **is** performed. The capture layer is the source of truth for what was suppressed;
  a clean preview report is necessary but not sufficient evidence that nothing dangerous fired.
- **Behavioural drift between the in-process path and the Temporal path.** Two execution paths exist
  (`DefaultProcessRunner.runDirectly(...)` for Preview / Explain and inside-RUN fallback, and `launchWithTemporal(...)`
  for RUN with a Temporal launcher present). Divergence between them is a recurring class of bug — the same call has
  happened twice already when a new feature only lands on one path. Compensation is the canonical example: preview
  exercises `compensationLogic()` synchronously, but the production saga fires from the Temporal activity boundary.
- **Preview can hang and must be timed out.** That is why the preview / explain dispatch goes through a bounded executor
  (`cbsNovaPreviewDispatchExecutor`, `cbs.nova.preview.execution.timeout-ms` default 20s, `poolSize` default 4) and
  `DispatchStage` records `dsl.preview.timeout` and returns `PreviewErrorCode.PREVIEW_TIMEOUT` (HTTP 504) on overrun.
  Cancellation is cooperative (interrupt-based), so a pure CPU spin loop inside user code will keep its worker thread
  until it exits; the pool is bounded and named for diagnosability.

**Neutral**

- `DslRuntime` has a single implementation (`DevDslRuntime`) regardless of environment; the prod-vs-preview decision is
  one layer down in `DefaultProcessRunner.resolveTemporalLauncher(...)`. This keeps the Spring surface small but means
  "switching to a different `DslRuntime`" is not a supported extension point — new behaviour is added by a new
  `BeanPostProcessor` or a new stage in the pipe, not by implementing the interface again.
- Preview caching (`PreviewCacheStage` + `PreviewResultCache`) trades freshness for latency; the default TTL is
  `cbs.nova.preview.cache.ttl-ms` (5 minutes). Operators tuning the cache must accept that cached reports can mask a
  definition that just changed.

## Cross-links

- [ADR 0001 — Temporal as the orchestration engine](0001-temporal-orchestration.md) — the production engine whose
  semantics Preview deliberately does not reproduce.
- [ADR 0002 — Helper SPI](0002-helper-spi.md) — the helper resolution mechanism whose behaviour `FakeHelperInterceptor`
  layers on top of in preview.
- [ADR 0003 — BFF Nitro admin UI plugin](0003-bff-nitro-admin-ui-plugin.md) — the admin surface through which the
  Preview / Explain endpoints reach the browser.
- [`docs/dsl/preview-mode.md`](../dsl/preview-mode.md) — end-user-facing howto, report schema, when-to-use; the
  authoritative reference for the *PreviewReport* record and the run-mode REST contract.
- [`docs/architecture-backend.md` — Operational modes](../architecture-backend.md) — the system-level view this ADR
  unpacks.
- [`docs/ideas/dry-run-preview-explain.ignore.md`](../ideas/dry-run-preview-explain.ignore.md) — the brainstorm that
  seeded this design. **Superseded as the architectural reference** by this ADR; kept in `docs/ideas/` for historical
  context only.