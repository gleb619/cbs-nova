# Phase 3 — Java DSL Pivot & Prototype (v0.7)

> **Goal:** Abandon Kotlin Script (`.kts`) and the Kotlin scripting host. Replace with a **Java DSL** that uses
> fluent builder APIs. Support dual execution modes: `GENERATED` (compile-time code generation of Temporal
> workflows/activities via Java APT in `dsl-codegen`) and `REFLECTED` (reflection-based runtime interpretation
> for dev). Build an MVP proving: **Java DSL → generated code → executes a Spring bean** for Event + Transaction + Helper.

---

## Migration Strategy

1. **dsl-api** — Convert remaining Kotlin data classes (`EventDefinition`, `TriggerDefinition`, etc.) to Java records/interfaces.
2. **dsl** — Delete ALL Kotlin files (script host, compilers, builders). Rewrite runtime in pure Java.
3. **dsl-codegen** — Extend APT processor to support `EventDefinition` and `WorkflowDefinition`; add generators for Temporal Workflow/Activity wrappers.
4. **starter** — Rewrite `TemporalTransactionsScope` from Kotlin to Java; update workflow/activity implementations for new Java DSL registry.
5. **backend** — Replace `.kts` script loading with Java DSL class loading / registry population.
6. **Prototype** — End-to-end test: write a `.java` DSL file → compile → generate Temporal wrappers → execute through Temporal TestWorkflowEnvironment → Spring bean is called.

---

## Task Table

| ID   | Title                                                                                                           | Status      | Module               | Blocked By       | Blocks          |
|------|-----------------------------------------------------------------------------------------------------------------|-------------|----------------------|------------------|-----------------|
| T41  | E2E tests in `starter` with `TestWorkflowEnvironment` + real `.java` DSL resources                              | `parked`    | starter              | T38d, T39, T40   | —               |
| T42  | Helper DSL — add `parameters` and `context` closures (Kotlin DSL era — **superseded by T80**)                  | `parked`    | dsl                  | T41              | T46, T47        |
| T43  | Transaction DSL — add `parameters` and `context` closures (Kotlin DSL era — **superseded by T80**)            | `parked`    | dsl                  | T41              | T46             |
| T44  | Condition DSL — add `parameters` and `context` closures (Kotlin DSL era — **superseded by T80**)              | `parked`    | dsl                  | T41              | T46             |
| T45  | MassOperation DSL — add `parameters` closure (Kotlin DSL era — **superseded by T80**)                          | `parked`    | dsl                  | T41              | T46             |
| T46  | Code-based helper import via import preprocessor (Kotlin DSL era — **obsolete, delete with script layer**)     | `parked`    | dsl                  | T42, T43, T44    | T47, T48        |
| T47  | Create `dsl-codegen` gradle module with annotation processor for `@DslComponent` registration                  | `done`      | dsl-api, dsl-codegen | —                | T64, T65        |
| T48  | Redesign `TestHelper` as real executable implementation with optional `execute` override                        | `todo`      | dsl                  | —                | —               |
| T49  | Replace `CodeImportResolver` runtime classpath scanning with compile-time generated SPI                         | `done`      | dsl                  | —                | —               |
| T50  | Integrate Avaje Jsonb and document JSON-native DSL parameters                                                   | `done`      | dsl-api              | T49              | T51-T58         |
| T51  | Consolidate `dsl-api` types by domain to reduce file count                                                      | `done`      | dsl-api              | T49              | T52-T58         |
| T52  | Unify `HelperDefinition` — merge `HelperFunction`, add parameter-aware `HelperInput`                            | `done`      | dsl-api, dsl         | T51              | T58             |
| T53  | Add `TransactionInput`/`TransactionOutput` and unify `TransactionDefinition`                                    | `done`      | dsl-api, dsl         | T51              | T58             |
| T54  | Add `EventInput`/`EventOutput` and unify `EventDefinition`                                                      | `done`      | dsl-api, dsl         | T51              | T58             |
| T55  | Add `ConditionInput`/`ConditionOutput` and unify `ConditionDefinition`                                          | `done`      | dsl-api, dsl         | T51              | T58             |
| T56  | Add `WorkflowInput`/`WorkflowOutput` and unify `WorkflowDefinition`                                             | `done`      | dsl-api, dsl         | T51              | T58             |
| T57  | Add `MassOperationInput`/`MassOperationOutput` and unify `MassOperationDefinition`                              | `done`      | dsl-api, dsl         | T51              | T58             |
| T58  | Extend `dsl-codegen` processor to extract required/optional parameter metadata from Input types                 | `parked`    | dsl-codegen          | T52-T57          | —               |

### New Tasks — Java DSL Pivot

| ID   | Title                                                                                                           | Status      | Module               | Blocked By       | Blocks          |
|------|-----------------------------------------------------------------------------------------------------------------|-------------|----------------------|------------------|-----------------|
| T60  | **Convert `dsl-api` Kotlin files to Java** — `EventDefinition`, `TriggerDefinition`, `TransitionRuleDefinition`, `LockDefinition`, `ParameterDefinition`, `SourceDefinition` | `todo` | dsl-api | — | T61-T63 |
| T61  | **Delete Kotlin script/compiler infrastructure from `dsl`** — `ScriptHost`, `DslCompiler`, `DslScopeExtractor`, `EvalResult`, all `*ScriptCompilationConfiguration`, `ImportParser`, `ImportResolver`, `CodeImportResolver`, `DslValidator`, `ValidationError` | `todo` | dsl | T60 | T62, T74 |
| T62  | **Rewrite `dsl` runtime in Java** — `DslRegistry`, `EventBuilder`, `TransactionBuilder`, `HelperBuilder`, `ConditionBuilder`, `WorkflowBuilder`, `MassOpBuilder`, `ParametersScope`, `StepHandleImpl`, `StepNode`, `TransitionScope`, `InMemoryTransactionsScope` | `todo` | dsl | T60 | T63, T68, T70 |
| T63  | **Rewrite `dsl` impl layer in Java** — `ImplRegistry`, `SpiImplRegistryLoader` (replace Kotlin `object` and `fun` extensions) | `todo` | dsl | T60, T62 | T68, T70 |
| T64  | **Extend `dsl-codegen` processor for Event + Workflow** — add `EventDefinition` and `WorkflowDefinition` to `INTERFACE_TYPE_MAP`; validate no-arg constructors; generate SPI registration | `todo` | dsl-codegen | T60 | T65, T66 |
| T65  | **Generate Temporal Workflow wrappers** — `RegistrationGenerator` produces `Generated{Code}EventWorkflow` implementing `EventWorkflow` interface, delegating to `EventDefinition.execute()` | `todo` | dsl-codegen | T64 | T72 |
| T66  | **Generate Temporal Activity wrappers** — `RegistrationGenerator` produces `Generated{Code}TransactionActivity` implementing `TransactionActivity` interface, delegating to `TransactionDefinition.execute()` | `todo` | dsl-codegen | T64 | T72 |
| T67  | **Rewrite `TemporalTransactionsScope` from Kotlin to Java** — remove `runBlocking`/`suspend`, use Temporal `Promise`/`Async` directly in Java | `todo` | starter | T62 | T70 |
| T68  | **Rewrite `DslLoader` for Java DSL** — scan configured directory for `.class` files or compiled `.java` DSL definitions; instantiate and register into `DslRegistry` (no `.kts` evaluation) | `todo` | backend | T62, T63 | T69, T70 |
| T69  | **Rewrite `DevDslController` for Java DSL** — accept Java source/class reference instead of raw `.kts` string; use `ReflectiveEventExecutor` to run events without code generation | `todo` | backend | T68, T73 | — |
| T70  | **Update `EventWorkflowImpl` for Java DSL registry** — remove `TransitionRuleDefinition` Kotlin-isms; use Java `EventDefinition` and `WorkflowDefinition` interfaces | `todo` | starter | T62, T63, T67 | T72 |
| T71  | **Update `TransactionActivityImpl` for Java DSL registry** — use Java `TransactionDefinition` interface; remove Kotlin-specific accessor patterns | `todo` | starter | T62 | T72 |
| T72  | **Prototype E2E test** — `TestEvent.java` DSL → `@DslComponent` → compile → generated Temporal workflow/activity → `TestWorkflowEnvironment` → `TestTransactionBean` is executed | `todo` | starter, dsl-codegen | T65, T66, T70, T71 | — |
| T73  | **`ReflectiveEventExecutor` for dev mode** — Java reflection-based executor that runs `EventDefinition` directly (no Temporal, no code generation) for fast local feedback | `todo` | dsl | T62 | T69 |
| T74  | **Remove Kotlin dependencies from `dsl/build.gradle`** — delete `kotlin.jvm`, `kotlin.serialization`, `kotlin.scripting.*` deps; switch to pure Java module | `todo` | dsl | T61, T62 | T75 |
| T75  | **Update module build files** — ensure `dsl-api` is pure Java; adjust `backend`/`starter` deps if needed; remove Kotlin script runtime deps from app classpath | `todo` | build | T74 | — |
| T76  | **Create sample `.java` DSL files in `examples/cbs-rules/`** — `TestEvent.java`, `TestTransaction.java`, `TestHelper.java` demonstrating fluent builder API | `todo` | examples | T62 | T72 |
| T77  | **Update `DslConfig`** — remove `ScriptHost` bean; register `ImplRegistry` + `SpiImplRegistryLoader` at startup; wire `ReflectiveEventExecutor` behind `@Profile("dev")` | `todo` | backend | T63, T73 | T68 |
| T78  | **Java DSL builder fluent API design** — `EventDsl`, `TransactionDsl`, `HelperDsl` entry-point interfaces with `.event("CODE")`, `.transaction("CODE")`, `.helper("CODE")` methods returning builders | `todo` | dsl-api | T60 | T62 |
| T79  | **Generate `EventWorkflowRequest` / `TransactionActivityInput` mappers** — `dsl-codegen` generates type-safe input mappers from `EventDefinition.getParameters()` metadata | `todo` | dsl-codegen | T58, T65 | — |
| T80  | **Unified Java DSL builder with `parameters`, `context`, `display` closures** — single builder class per entity supporting all documented blocks from `dsl-design.md` | `todo` | dsl | T78 | T76, T72 |

---

## Dependency Graph (New Tasks)

```
T60 ─┬─> T61 ──> T74 ──> T75
     ├─> T62 ─┬─> T63 ─┬─> T68 ──> T69
     │        │        │         └─> T70 ──> T72
     │        │        └─> T77
     │        └─> T67 ──> T70
     │        └─> T73 ──> T69
     │        └─> T80 ──> T76 ──> T72
     │        └─> T71 ──> T72
     └─> T78 ──> T62, T80
     └─> T64 ─┬─> T65 ──> T72
              └─> T66 ──> T72
              └─> T79 (optional)
```

---

## MVP Definition (Event + Transaction + Helper)

**Acceptance criteria for T72 (Prototype E2E test):**

1. A developer can write:
   ```java
   // examples/cbs-rules/TestEvent.java
   package cbs.rules;

   import cbs.dsl.api.EventDsl;

   public class TestEvent {
       public static void define(EventDsl dsl) {
           dsl.event("TEST_EVENT")
               .requiredParam("customerId")
               .context(ctx -> {
                   ctx.put("enriched", ctx.helper("TEST_HELPER", Map.of("id", ctx.get("customerId"))));
               })
               .transactions(scope -> {
                   scope.step("TEST_TRANSACTION");
               })
               .build();
       }
   }
   ```

2. A `@DslComponent(type = DslImplType.TRANSACTION)` Spring bean `TestTransaction` implements `TransactionDefinition`.
3. A `@DslComponent(type = DslImplType.HELPER)` Spring bean `TestHelper` implements `HelperDefinition`.
4. `dsl-codegen` APT processor generates:
   - `GeneratedImplRegistrations` (SPI file + registration class)
   - `GeneratedTestEventWorkflow` (Temporal workflow implementing `EventWorkflow`)
   - `GeneratedTestTransactionActivity` (Temporal activity implementing `TransactionActivity`)
5. `TestWorkflowEnvironment` test starts the generated workflow + activity.
6. Workflow execution reaches the transaction activity, which delegates to `TestTransaction.execute()`, which returns a result.
7. Test asserts the transaction bean was invoked with correct parameters.

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Delete all Kotlin in `dsl` (77 files) | The scripting host, compiler, and runtime builders are deeply coupled to Kotlin Script APIs. Rewriting in Java is cleaner than incremental migration. |
| Keep `dsl-api` interfaces but convert to pure Java | The interface surface (`TransactionDefinition`, `HelperDefinition`, etc.) is already mostly Java and used by `starter` and `backend`. Kotlin-isms (`suspend`, data class, sealed class) must be removed. |
| `dsl-codegen` generates BOTH SPI registration AND Temporal wrappers | The existing generator already handles SPI. Extending it to generate `EventWorkflow` and `TransactionActivity` implementations keeps the build single-pass. |
| `REFLECTED` mode uses `ReflectiveEventExecutor`, not generated code | Dev mode needs fast iteration. A reflection-based executor interprets `EventDefinition` directly without compiling generated sources. |
| `DslLoader` loads compiled classes, not raw `.java` source | The Java DSL is compiled normally. `DslLoader` discovers `@DslComponent` classes or classes with `static define(EventDsl)` methods via classpath scanning or explicit config. |

---

## Files to Delete (Kotlin DSL Era)

**`dsl/src/main/kotlin/` (all 47 files):**
- `cbs/dsl/compiler/*` — 8 files (compiler, validator, import resolution)
- `cbs/dsl/script/*` — 13 files (script host, compilation configs, scopes, extractors)
- `cbs/dsl/runtime/*` — 16 files (Kotlin builders)
- `cbs/dsl/impl/*` — 2 files (`SpiImplRegistryLoader.kt`, `ImplRegistry.kt`)
- `cbs/dsl/runner/*` — 1 file (`InMemoryTransactionsScope.kt`)

**`dsl/src/test/kotlin/` (all 4 files):**
- `cbs/dsl/compiler/CodeImportResolverTest.kt`
- `cbs/dsl/compiler/ImportParserTest.kt`
- `cbs/dsl/compiler/InMemoryRulesSource.kt`
- (plus any other test files)

**`dsl-api/src/main/kotlin/` (all 6 files):**
- `cbs/dsl/api/EventDefinition.kt`
- `cbs/dsl/api/TriggerDefinition.kt`
- `cbs/dsl/api/TransitionRuleDefinition.kt`
- `cbs/dsl/api/LockDefinition.kt`
- `cbs/dsl/api/ParameterDefinition.kt`
- `cbs/dsl/api/SourceDefinition.kt`

**`starter/src/main/kotlin/` (1 file):**
- `cbs/temporal/TemporalTransactionsScope.kt`

---

## Risk Register

| Risk | Mitigation |
|------|------------|
| Kotlin → Java migration breaks existing `starter` tests | Keep `starter` test suite green by updating tests incrementally as interfaces change. T72 provides the E2E safety net. |
| Temporal code generation is complex | Start MVP with simple delegation: generated workflow calls `eventDefinition.execute()` directly. No async optimization in prototype. |
| `EventDefinition` was Kotlin `interface` with `suspend` block | T60 redesigns `EventDefinition` as Java interface. `transactionsBlock` becomes `Consumer<TransactionsScope>` instead of suspend lambda. |
| `TransitionRuleDefinition` was Kotlin `data class` with `suspend` fault block | T60 redesigns as Java `record` or immutable class. `onFaultBlock` becomes `Consumer<TransactionsScope>` or `BiConsumer`. |
| `dsl-codegen` APT can only see classes in compilation unit | DSL files in `cbs-rules/` repo must be compiled together with the annotation processor, or the processor must run against a JAR of DSL definitions. Document this in build design. |
