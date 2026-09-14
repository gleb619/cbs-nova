# 0006. Retire in-process Java compilation (`JavaSourceCompiler`)

- **Status:** Proposed
- **Date:** 2026-09-14

## Context

The DSL reload flow currently has two compilation paths behind a single consumer,
`cbs.nova.starter.controller.DslReloadHandler` (`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/controller/DslReloadHandler.java`).
Which path runs is decided at request time by whether a `DslBuilderClient` bean is present
(`csb.dsl.builder-client.enabled`, default `true`, wired in
`BuilderClientConfiguration` under
`@ConditionalOnProperty(prefix = "csb.dsl.builder-client", name = "enabled", havingValue = "true", matchIfMissing = true)`):

- **Remote path (default today).** `DslReloadHandler.compileSources(...)` posts DSL sources to the
  dsl-builder service via `DslBuilderClient.compile(...)`, downloads the resulting zip via
  `DslBuilderClient.downloadZip(...)`, extracts it into a temp `outputDir`, and lets the existing
  reload pipeline stage the new class loader. The Gradle Tooling API work runs **inside the
  dsl-builder JVM**, fully isolated from the starter process.
- **In-process fallback.** When the builder client is absent, `DslReloadHandler` falls back to
  `cbs.nova.starter.service.JavaSourceCompiler`
  (`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/service/JavaSourceCompiler.java`),
  which calls `javax.tools.ToolProvider.getSystemJavaCompiler()` against the configured DSL
  `sourceDir`, hands `java.class.path` as the classpath, and writes `.class` files into the same
  `outputDir` the remote path uses. The class is annotated `@Deprecated(forRemoval = true)` and
  carries the source comment `//TODO: force remove, its forbidden to call compilation from a
  java process`; the fallback method in `DslReloadHandler` carries the matching
  `// TODO: use a `dsl-builder` instead` and its own `@Deprecated(forRemoval = true)`.

The four problems the in-process path introduces:

- **Runtime JDK requirement.** `ToolProvider.getSystemJavaCompiler()` returns `null` on a JRE;
  `JavaSourceCompiler.compile(...)` then throws
  `IllegalStateException("No system Java compiler available (JDK required)")`. Operating the
  starter on a JRE — already the default expectation for any embedded-service Spring Boot
  workload — is silently incompatible with the fallback path. ADR 0005 pins the runtime JDK at 25,
  which makes shipping a JDK with the runtime an explicit operational ask rather than an
  implementation detail.
- **Classpath leakage.** `JavaSourceCompiler.compile(...)` reads
  `System.getProperty("java.class.path")` and passes it as the `-classpath` option to `javac`.
  Every starter dependency on the runtime classpath — including Temporal SDK internals, Spring
  infrastructure, Micronaut/REST clients, the starter's own classes — is exposed to DSL source
  compilation. A DSL author can therefore write code that resolves against starter internals and
  compiles cleanly here but fails to compile in any other build pipeline (CI, dsl-builder, the
  user's own `javac`). This is a correctness hazard, not just a hygiene problem: the same DSL
  set is observably compiled two different ways depending on which path the operator has
  configured.
- **Compiler JVM contention.** `JavaFileManager` / `DiagnosticCollector` / `JavaCompiler.CompilationTask`
  hold a non-trivial share of heap, metaspace, and JIT code-cache on the same JVM as the Spring web
  server, the Temporal worker threads, and the preview dispatch pool. A reload mid-load — exactly
  the moment the workbench publish flow triggers one — can stall web requests, the preview
  executor, and the worker's pollers.
- **Security and audit surface.** A `javac` task is a programmable compiler: source files compiled
  in-process can pull arbitrary classes from the runtime classloader and resolve internal-package
  types that the user's DSL should never see. Running a programmable compiler inside the process
  that also serves authenticated traffic widens the blast radius of any DSL-side mistake.

The off-ramp the ADR recommends **already ships in this repo**. `cbs.nova.dsl.builder` is a
Spring Boot service under `backend/dsl-plugins/dsl-builder/` that compiles DSL sources through the
**Gradle Tooling API** in a separate JVM. `cbs.nova.dsl.builder.service.CompileService`
(`backend/dsl-plugins/dsl-builder/src/main/java/cbs/nova/dsl/builder/service/CompileService.java`)
writes a `settings.gradle` and a templated `build.gradle` into a per-session workspace, copies
sources in, and calls
`cbs.nova.dsl.builder.service.GradleService.runBuild(...)`
(`backend/dsl-plugins/dsl-builder/src/main/java/cbs/nova/dsl/builder/service/GradleService.java`),
which uses `org.gradle.tooling.GradleConnector` to fork a Gradle worker process with a separately
configured `gradleJavaHome`. The compile output is zipped and streamed back via
`cbs.nova.dsl.builder.controller.CompileController` (`/api/dsl/compile` and
`/api/dsl/compile/{id}/download`). This is the same pipeline the dsl-builder uses for draft
publish / bulk import — it is in production today.

## Consumer analysis: `DslReloadHandler` and the surfaces behind it

`DslReloadHandler` is registered by `DslReloadRouterConfiguration` as a Spring
`RouterFunction` bean, gated by `cbs.dsl.reload.enabled` (default `true`). It exposes
`POST /api/dsl/reload` and a programmatic `reloadDefinitions()` method used by draft publish
and bundle import. The handler is the only consumer of `JavaSourceCompiler`.

**Reload surfaces that go through this handler:**

- `POST /api/dsl/reload` → proxied by the BFF at
  `frontend/admin-ui-plugin/server/api/v1/dsl/reload.post.ts` →
  `useDslApi.reload()` (`frontend/admin-ui-plugin/app/composables/useDslApi.ts:288`) →
  `useDslWorkbench.reloadDefinitions()` (`frontend/admin-ui-plugin/app/composables/useDslWorkbench.ts:270`).
  In `dsl-workbench.vue`, the workbench's top-bar **Refresh** action and the
  `onHistoryRestored()` handler both call `reloadDefinitions()`.
- `cbs.nova.starter.controller.DslDraftHandler.finishPublish(...)` calls
  `reloadHandler.reloadDefinitions()` after a successful draft publish (line 641). The workbench's
  **Publish** action goes through this path via the draft `publishDraft` endpoint and the BFF
  draft proxy.
- `cbs.nova.starter.controller.DslDraftHandler.importBundleViaBuilder(...)` calls
  `reloadHandler.reloadDefinitions()` after a successful bundle import (line 539). Bundle import
  is reached from the `POST /api/dsl/definitions/import` route in
  `cbs.nova.starter.config.router.DslDefinitionBundleRouterConfiguration` →
  `DslDraftHandler.importBundle`.

**Tests and downstream callers.** The handler's programmatic path is exercised by
`DslReloadResourceTest.reloadDefinitions()` and by `DslVersioningIntegrationTest`, both of which
expect `LoadResult` back; both run against the same conditional toggle, so they exercise only the
remote path under default config.

What breaks under each option is determined entirely by what happens to `DslReloadHandler.compileSources(...)`.

## Options

### 1. Route reload through dsl-builder's `GradleService` (the existing off-ramp)

Drop the `JavaSourceCompiler` branch in `DslReloadHandler.compileSources(...)`. With the builder
client already `true` by default, this is a no-op for default deployments; for operators who
currently set `csb.dsl.builder-client.enabled=false`, the option is to either flip the toggle on
or accept that reload no longer works in that mode. The workbench "Refresh" and history-restore
flows continue to work, because the remote path is already wired end-to-end through the existing
`DslBuilderClient.compile(...)` → `downloadZip(...)` → zip-extract path. `DslReloadHandler`'s
public contract (`reload(...)`, `reloadDefinitions()`, the lock semantics, the failure-safe
`GlobalManager` swap, the `PreviewResultCache.clear()` flush, the `DslAuditService` audit, the
best-effort `DomainEvent.ReloadFailed`) is unchanged. The only behavioural change is a longer
reload tail latency because the Gradle build now runs every time, even on hot reloads, instead
of being short-circuited by `ToolProvider.getSystemJavaCompiler()`'s in-process compile. The
classpath now is whatever the dsl-builder's `build.gradle` template pins
(`backend/dsl-plugins/dsl-builder/src/main/resources/templates/`), so the leakage in option-2 is
eliminated by construction.

### 2. Subprocess `javac` instead of `ToolProvider`

Replace the in-process `JavaCompiler.CompilationTask` call with a `ProcessBuilder` invocation of
`javac` in a forked JVM. The `compileSources` method would have to assemble the classpath itself
(the helper codegen classes, the dsl-api module, the runtime support libraries that the starter
exposes to DSL sources) — there is no Gradle Build to lean on. This is the lightest-weight
short-term removal: it preserves the "no external service" mode and removes the in-JVM compiler
hazard. Cost: two javac toolchains to keep in sync (the local `javac` used here vs. the one
Gradle uses in dsl-builder), no daemon warm-up benefit, and the classpath-assembly problem is
still ours to solve — there is no build script to point at. Each DSL source-set change risks a
classpath drift between this path and the dsl-builder path.

### 3. Drop dynamic recompile-on-reload entirely

Make reload accept only prebuilt artifacts. The handler would no longer compile; the operator
either ships compiled classes on the classpath (e.g. via a Docker build that runs the same Gradle
build dsl-builder does) or reload is removed as a feature. The UI surfaces documented above
(`useDslWorkbench.reloadDefinitions()`, the workbench **Refresh** action, the `onHistoryRestored()`
hook, and the post-publish reload in `DslDraftHandler.finishPublish`) would either become no-ops
or be removed. Removing the reload after publish from `DslDraftHandler.finishPublish(...)` is the
largest breaking change in this option, because today a freshly published draft only becomes
"live" through that reload — without it, a successful publish does not change what the workbench
or runner observes until they restart.

### 4. Keep `JavaSourceCompiler` as-is and document the risk

Rejected as a category, because the existing class already carries
`@Deprecated(forRemoval = true)` and the source TODO the team left for itself. Documenting the
risk does not retire the deprecated code, and a JRE-only starter remains operationally correct
while `JavaSourceCompiler` is on the classpath: `ToolProvider.getSystemJavaCompiler()` simply
returns `null`, and the `IllegalStateException` is now reachable from a default deployment that
flips `csb.dsl.builder-client.enabled=false` for any reason.

## Decision

We will adopt **Option 1**. The remote path is already the default
(`csb.dsl.builder-client.enabled=true`, see `DslBuilderClientProperties` and
`BuilderClientConfiguration`'s `matchIfMissing = true`); the dsl-builder service compiles via the
Gradle Tooling API in a separate JVM (`CompileService.compileInternal(...)` →
`GradleService.runBuild(...)` → `GradleConnector.newConnector()...connect()`); the reload handler
already uses that path by default and only falls back to `JavaSourceCompiler` when the builder
client is disabled. Removing the fallback deletes the deprecated class and the deprecated
`compileSources` branch, and the operational mode it represented — running a compiler inside the
process that also serves authenticated traffic — goes with it.

We will keep the toggle (`csb.dsl.builder-client.enabled`) for now so operators who depend on a
no-builder-client mode have a documented upgrade window. In the same window, the `@Deprecated(forRemoval = true)`
on `JavaSourceCompiler` becomes effective (the class is deleted and the toggle is removed in a
follow-up row). The deprecation annotation is the leverage the rest of this plan needs.

We are **not** adopting Option 2 because the classpath-assembly problem moves from Gradle to us
without removing the underlying hazard. We are **not** adopting Option 3 because the workbench
**Refresh** action and the post-publish reload are user-visible behaviours the brief explicitly
asks us to preserve; cutting them is a feature change, not a refactor. Option 4 is rejected
categorically (see above).

## Consequences

**Positive**

- The starter no longer needs a JDK at runtime to support reload; a JRE image is sufficient.
  Container footprint shrinks by whatever the JDK adds in the operator's base image.
- The DSL compile classpath is whatever the dsl-builder's `build.gradle` template pins, identical
  to what every other consumer of those DSL classes (CI, the user's own build) sees. The
  "compiles here, fails in CI" hazard described in the Context goes away.
- Compiler CPU, heap, and metaspace pressure move out of the starter JVM. The Temporal worker
  threads, the preview dispatch executor, and the Spring web server stop sharing their JIT and GC
  with a `JavaCompiler.CompilationTask`.
- The `//TODO: force remove, its forbidden to call compilation from a java process` comment that
  has been sitting on `JavaSourceCompiler` is finally honoured.

**Negative**

- Every reload now pays Gradle's cold/warm start cost (template scaffold, settings / build file
  copy, Gradle daemon warm-up). For an idle, freshly published draft this is the dominant reload
  time. Operators who relied on sub-second reloads will see a step change.
- The starter is now coupled to dsl-builder availability for the reload flow. If dsl-builder is
  down, `reloadHandler.reloadDefinitions()` returns `DslCompilationException` ("DSL builder
  unavailable: …") and the live registry is untouched — the existing failure-safe swap in
  `doReload(...)` already handles this, so the runtime is not bricked, but reload becomes a no-op
  until dsl-builder recovers.
- `csb.dsl.builder-client.enabled=false` deployments lose the reload flow. This is a breaking
  configuration change for anyone running with the builder client disabled today. The toggle stays
  in the code for a deprecation window, but the path it enabled goes away.

**Neutral**

- The `@Deprecated(forRemoval = true)` annotations on `JavaSourceCompiler` and on
  `DslReloadHandler.compileSources(...)` become the public signal that the in-process path is on
  the way out. Both are removed in a follow-up row after the deprecation window.
- The workbench UX does not change: `useDslWorkbench.reloadDefinitions()` still calls
  `api.reload()` which still hits `POST /api/dsl/reload`; the only observable delta is reload
  latency.
- `DslReloadHandler.reloadDefinitions()` and `reload(ServerRequest)` keep their existing
  failure-safe contract (compile or stage fails → live `GlobalManager` untouched,
  `PreviewResultCache` not flushed, `ReloadResponse` carries the
  `DslCompilationException.diagnostics()`).

## Phased removal plan

This ADR records the decision. Implementation is **not** part of this task. After human review
of this ADR, follow-up kanban rows land in `docs/kanban.md` (created by the human reviewer, not
by this loop):

1. **Compatibility window.** While the toggle is still respected: delete `JavaSourceCompiler.java`
   and delete the in-process branch from `DslReloadHandler.compileSources(...)`. With the
   builder client defaulting to `true`, default deployments are unaffected. Deployments with
   `csb.dsl.builder-client.enabled=false` get a clean startup failure with a clear message
   pointing at the toggle, instead of a deferred `IllegalStateException("No system Java compiler
   available (JDK required)")`. Add a `CHANGELOG` / release-note entry that calls out the
   removal.
2. **Toggle removal.** After the compatibility window has shipped at least one minor release,
   remove `csb.dsl.builder-client.enabled`, the `matchIfMissing = true` gating in
   `BuilderClientConfiguration`, and the `ObjectProvider<DslBuilderClient>` fallback in
   `DslReloadHandler`. The builder client becomes mandatory.
3. **Observability for the new latency profile.** The BFF and starter expose reload latency
   metrics (existing `cbsNovaPreviewDispatchExecutor` precedent in ADR 0004 shows the naming
   convention). Operators tuning reload budget have something to chart.

Acceptance for this ADR: the ADR file is reviewed and either Accepted or sent back for revision
in `docs/kanban.md`. No source file is touched and no follow-up row is created in this task.

## Cross-links

- [ADR 0004 — Preview, dry-run, and Explain execution modes](0004-preview-dry-run-explain-modes.md)
  — the same `DslReloadHandler` flushes `PreviewResultCache` after a successful registry swap, so
  the preview-mode behaviour depends on reload working at all.
- [ADR 0005 — Run the backend platform on Spring Boot 4, Jackson 3, and Java 25](0005-platform-baseline-spring-boot-4-jackson-3.md)
  — pins the JDK at 25; this ADR removes the operational need for the starter to *be* a JDK at
  runtime.
- [`docs/plans/T499-adr-remove-inprocess-compilation.md`](../plans/T499-adr-remove-inprocess-compilation.md)
  — the brief this ADR implements.
- `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/service/JavaSourceCompiler.java` —
  the class this ADR retires (read-only after this ADR is Accepted; deleted in phase 1).
- `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/controller/DslReloadHandler.java` —
  the single consumer; `compileSources(...)` (line 220, preceded by `@Deprecated(forRemoval = true)`
  on line 219) is the fallback this ADR retires.
- `backend/dsl-plugins/dsl-builder/src/main/java/cbs/nova/dsl/builder/service/CompileService.java`
  and `…/service/GradleService.java` — the off-ramp Option 1 routes reload through.
- `frontend/admin-ui-plugin/server/api/v1/dsl/reload.post.ts` and
  `frontend/admin-ui-plugin/app/composables/useDslWorkbench.ts` (`reloadDefinitions()`) — the UI
  surface whose behaviour this ADR preserves.