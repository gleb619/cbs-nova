# Agent Guide: cbs-nova Backend

Guide for coding agents on the Temporal DSL Orchestration Engine Java backend. Architecture: `docs/architecture-backend.md`, `docs/dsl/`.

---

## 1. Project Map & Architecture

Operational modes: `run` (Temporal), `preview` (in-process dry-run), `explain` (dry-run + Mermaid).
Constructs: **Process** (Workflow), **Transaction** (Activity), **Function** (helper), **Helper** (SPI).

```
backend/
├── dsl-platform/    dsl-api (contracts) | dsl (runtime) | dsl-codegen (AP) | misc-codegen (@Helper SPI)
├── dsl-plugins/     dsl-gradle-plugin | dsl-idea-plugin | dsl-builder (Spring Boot :8091, Tooling API + JGit)
└── dsl-starter/     dsl-examples (JEP-512 compact) | starter (REST) | starter-launcher (host :8080)
```

Dependency: `dsl-api` <- `dsl` <- `dsl-codegen` / `starter` / `dsl-examples` / `dsl-gradle-plugin`.
Execution: Generated -> `GlobalManager.getInstance()` -> Managers -> Runners -> Registries.

---

## 2. Core Rules & Constraints

### DSL Authoring (`dsl-examples`)
- Compact sources: one `List<DslObject> define()`, no `class`/`package`/`public`.
- Model imports are free-style: `<basePackage>.<ModelClass>`, `<basePackage>.<version>.<ModelClass>`,
  `<version>.<ModelClass>`, or bare `<ModelClass>` all resolve via `ModelImportResolver` (rewritten to the
  canonical model package before javac). See `docs/dsl/authoring.md` §Model imports.
- `SourceCompiler` preprocesses into `cbs.nova.dsl.DslCompactSource` implementors, validates, `javac`s.
- `GeneratedDslDefinitionProvider` aggregates `define()` via `ServiceLoader`. `DefinitionLoader`: dir with `.java`
  -> preprocessor; else classpath `ServiceLoader`.
- `dsl-gradle-plugin` (`cbs.nova.dsl`) compacts DSL sources; compiler from Maven Local via `dslVersion`.
- `dsl-builder` (Spring Boot :8091): stages workspace, renders Gradle build applying `dsl-gradle-plugin`,
  checks out sources via JGit (`cbs.dsl.builder.git.repo-url` + `git.sub-path` + `git.worktrees-dir`; per-request `repoUrl`),
  runs Gradle Tooling API, exposes `POST /api/dsl/compile` + `GET /api/dsl/compile/{id}/download`.
  Compile via bounded `BuilderWorkQueue` (`cbs.dsl.builder.queue.*`; full -> HTTP 429). Hosts draft/file/vcs:
  `DraftController` (`/api/dsl/drafts/**`), `DefinitionBundleController` (`/api/dsl/definitions/export|import`),
  `FileController` (`/api/dsl/files/**`, Caffeine + bulkhead `cbs.dsl.builder.files|file-buffer.*`),
  `VcsController` (`/api/dsl/vcs/status`). Publish/restore/import write files only; registry reload stays in starter.
- Starter talks to builder via `cbs.nova.starter.builder.DslBuilderClient`
  (`csb.dsl.builder-client.enabled=true`, `csb.dsl.builder-client.base-url=http://localhost:8091`). Reload
  compiles remotely (HTTP/2 via JDK `HttpClient`, h2c prior-knowledge) and swaps registry locally;
  drafts/files/bundles/VCS delegate to builder. Bounded queue (`queue.*`), semaphore bulkhead (`bulkhead.*`),
  circuit breaker (`breaker.*`): builder 5xx/network -> 503 `BUILDER_UNAVAILABLE`; 429 -> 429 `BUILDER_BUSY`;
  compile failure -> 422 `DslCompilationException`. `enabled=false` -> in-process javac + local drafts/files.
- **Call Hierarchy**: **Process** -> Transactions/Helpers/Functions (never Processes).
  Transaction/Function/Helper/Compensation -> Helpers/Functions (never Processes/Transactions).
- DTOs/IO: Java `record`s annotated `@Json` (Avaje Jsonb).
- Helpers: implement `Executable<IN, OUT>`, `@Helper(name="...")` unique.
- Compensations: only Helpers and Functions.

### Coding Practices
- **Java**: default DTOs/payloads to `record`s.
- **Nullability**: `jspecify` annotations (`libs.jspecify`).
- **Context**: immutable. Use `ctx.withBody(...)` / `ctx.withMetadata(...)`.
- **GlobalManager**: never bypass facade or registries in generated code.
- **No reflection**: forbid `java.lang.reflect`, `Constructor.newInstance()`; prefer typed alternatives
  (ServiceLoader, generated registries, records, explicit interfaces). Build-time APs and test utilities exempt.
- **No edit generated code**: `*ProcessWorkflow` / `*ProcessDefinition` / `*TransactionActivity` / `*TransactionDefinition`
  come from `dsl-codegen` templates. Edit templates/DSL instead.
- **No `static final` for dynamic values**: magic numbers, paths, durations, retry/queue/breaker tunables, ports,
  feature flags, bulkhead capacities, breaker thresholds, cache TTLs must come from `application.yml`
  via `@ConfigurationProperties` or `@Value`. Reserve `static final` for compile-time constants only
  (regex, charset names, protocol strings, error-code enums). Rationale: hot-tunable, env overrides per profile,
  testability. See `StarterConstants.java` for the constants surface; new dynamic values get a
  `@ConfigurationProperties` class under `dsl-starter/.../config/`.
- **Style**:
  - Self-documenting code; no explanatory comments. Remove outdated/redundant/obvious comments.
  - Javadoc only on public API contracts where the signature cannot convey the info.
  - Simplest implementation; no clever one-liners, deep nesting, or large lambdas.
  - Small methods, single responsibility, descriptive names. No magic numbers, abbreviations, duplicated logic.
  - Lombok over boilerplate (`@Getter`/`@Setter`/`@Builder`/`@EqualsAndHashCode`/`@RequiredArgsConstructor`).
  - MapStruct for DTO/entity/domain mapping. No hand-rolled mappers.
  - 2-space indent, enforced by Spotless (`backend/gradle/code-style.gradle`).
  - Functional/stream style; method chains over imperative loops.
  - Files < 300 lines; split focused classes.
  - `@Builder` over long ctors; `@RequiredArgsConstructor` only for simple DI.
  - `@FunctionalInterface` for single-method abstractions; split monolithic classes into focused collaborators.

---

## 3. CLI Commands (from `backend/`)

`backend/build.gradle` delegates via Exec tasks. Order (platform -> plugins -> starter) automatic.

```bash
./gradlew build | publishToMavenLocal                    # all
./gradlew -p dsl-platform build|test|spotlessCheck|spotlessApply
./gradlew -p dsl-plugins build
./gradlew -p dsl-starter build
```

---

## CodeGraph

> Prereq: Node v22 — `source ~/.nvm/nvm.sh && nvm use v22.20.0`

Index under `backend/.codegraph/` (frontend has its own). Run from `backend/`:

```bash
codegraph status
codegraph query <SymbolName> --kind class --limit 5 --json
codegraph index --force   # after mass refactors
```

Prefer `codegraph_*` over grep.

---

## 4. Key Context & Recent Changes

- **Rich contexts** (dsl-api): `ProcessContext`, `TransactionContext`, `CompensationContext`, `FunctionContext<T>`.
- **Fluent APIs**: `ProcessContext.complete(Object)` returns result; `CompensationContext.log()` returns `CompensationContext<T>`.
- **Result casts**: `Result.as(Class)`, `Result.asMap()`. **Parameter DSL**: `.parameters(...)`, `ParameterRegistry`, `MapInput.of(k,v)`.
- **Heartbeat**: `TransactionBuilder.heartbeatTimeout(Duration)`.
- **SPI / Helper resolution**: `misc-codegen` generates `GeneratedHelperResolver` (registers with `GlobalManager`,
  honors `componentModel` STANDARD/LAZY + `creationStrategy` STANDARD/FACTORY) and `GeneratedHelperInstanceResolver`
  (`new X()` for FACTORY, `HelperInstanceResolver.resolve(X.class)` for STANDARD). Loaded via `META-INF/services` +
  `ServiceLoader`. `DslAutoConfiguration` exposes `SpringOrGeneratedHelperInstanceResolver` (Spring bean first,
  then generated factory, else `IllegalStateException`). No reflection fallback. `@SpringHelper` forces
  `componentModel=LAZY`, `creationStrategy=STANDARD`; registered as singleton Spring bean via
  `SpringHelperBeanDefinitionRegistrar`.
- **Expression evaluator**: default `cbs.nova.dsl.utils.MvelExpressionEvaluator` (MVEL). `DslConfig.expressionEvaluator()`
  returns `Replaceable<ExpressionEvaluator>` for swaps. Starter's `DslAutoConfiguration` publishes
  `ExpressionEvaluator` via `@ConditionalOnMissingBean` and replaces via
  `DslConfig.dslConfig().expressionEvaluator().replace(...)`; user-defined bean wins. See `docs/architecture-backend.md`.
- **Explain support**: `ExplainSupport<IN, OUT>` (dsl-api) produces `ExplainReport` (name, markdown, mermaid)
  via single-arg `explain(ctx)`; the budget is carried by context metadata under
  `Constants.EXPLAIN_BUDGET_CHARS_KEY` (default `Constants.DEFAULT_BUDGET_CHARS` = 4000), read via
  `cbs.nova.dsl.explain.ExplainBudget.of(ctx)`. DslObject explain logic is a ready, typed
  `Function<XContext<?>, Result<ExplainReport>>` (builder `.explain(...)`); with no explicit explain,
  `Executable.default explain()` composes a descriptor-based markdown report (never executeLogic).
  `.explainVia("file.md")` is lazy: markdown resolves at invocation through the injectable
  `cbs.nova.dsl.explain.ExplainResourceResolver` (`DslConfig.explainResourceResolver()` Replaceable,
  default `ClasspathExplainResourceResolver` with prefix `explain/`). Starter publishes
  `SpringExplainResourceResolver` via `@Bean @ConditionalOnMissingBean(ExplainResourceResolver.class)`
  from `CbsNovaExplainProperties.resourcesPrefix()` (`cbs.nova.explain.resources-prefix`, default
  `explain/`; same record carries `budgetChars`) and registers it into `DslConfig` in
  `dslApplicationRunner`; a user-defined `ExplainResourceResolver` bean wins. The pipe/stage explain
  chain lives in the starter (`core/pipe/ExplainDslPipe`, stages in `core/stage` such as
  `ExplainBudgetStage`/`ExplainReportStage`); helpers override `explain` for mode/arg-specific reports
  (see `MathHelper`). Starter builds full 14-field `ExplainGraphReport` (package `cbs.nova.dsl.model/` —
  graph-shaped: `children`, `hasCompensation`, self-rendering `toMermaid()`/`toPlantUml()`/`toBpmn()`);
  `DevDslRuntime` maps it to simple `ExplainReport` with one-line trace summary.

---

## 5. Agent Workflows

- **New DSL construct**: update `dsl/` API, `dsl-codegen/` templates/validation, add tests, update docs.
- **New runtime feature**: maintain `GlobalManager` facade, add JUnit tests, cover all three modes (`run`/`preview`/`explain`).
- **New Temporal example**: under `temporal-example/`, test with `TestWorkflowEnvironment`.

---

## 6. Onboarding Reading List

1. `docs/architecture-backend.md`
2. `docs/dsl/constructs.md` — execution contracts
3. `docs/dsl/authoring.md` — writing DSL flows
4. `docs/dsl/codegen.md` — generated code conventions
5. `docs/dsl/runtime.md` — registries, runners, managers