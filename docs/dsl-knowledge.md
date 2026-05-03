# DSL Knowledge — CBS Nova

**Stack:** Kotlin Script (.kts), Kotlin Scripting Host, Java 25, Gradle. Modules: `dsl-api` (shared API), `dsl` (
runtime + compiler).

**DSL File Types & Conventions:**

| Suffix             | Scope Class             | Builder              | Defines                                                    |
|--------------------|-------------------------|----------------------|------------------------------------------------------------|
| `.event.kts`       | `EventDslScope`         | `EventBuilder`       | parameters, context, display, transactions, finish         |
| `.transaction.kts` | `TransactionDslScope`   | `TransactionBuilder` | parameters, context, preview, execute, rollback            |
| `.helper.kts`      | `HelperDslScope`        | `HelperBuilder`      | parameters, context, execute                               |
| `.condition.kts`   | `ConditionDslScope`     | `ConditionBuilder`   | parameters, context, predicate                             |
| `.workflow.kts`    | `WorkflowDslScope`      | `WorkflowBuilder`    | states, initial, terminal, transitions                     |
| `.mass.kts`        | `MassOperationDslScope` | `MassOpBuilder`      | parameters, triggers, source, lock, context, item, signals |

One folder per event/workflow. Mass ops live under `mass-operations/`. Helpers can be global or event-scoped.

**Two-Pass Compilation:**

1. Pass 1 — eval all `.kts` files without imports, build merged `DslRegistry`
2. Pass 2 — parse `// #import` lines, resolve via `ImportResolver`, re-eval scripts with injected
   `imports: Map<String, ImportScope>`

**Import System:**
```kotlin
// #import loan-disbursement.* as disb
// #import global.banking-helpers
// #import code:cbs.dsl.impl.TestHelpers   ← code-based (T46)
```

`framework.*` imports are no-ops. `ImportParser` scans `// #import` comments. `ImportResolver` produces
`Map<String, ImportScope>`.

**Context Hierarchy:**
```
BaseContext(eventCode, workflowExecutionId, performedBy, dslVersion)
  └── ParameterContext(eventParameters)
        └── EnrichmentContext(enrichment: MutableMap)  ← operator fun set(key, value)
              ├── HelperContext(params)                 ← helper(name, params): Any
              ├── TransactionContext(isResumed)         ← prolong(Action), delegate()
              └── FinishContext(displayData)
```

**Registries:**

- `DslRegistry` — immutable-read, compilation-time registry of all definitions from `.kts` files. Strict: throws on
  duplicate code.
- `ImplRegistry` — runtime dispatch registry. Lookup priority: **name first, then code**. Last-write-wins (supports test
  overrides). `populateFrom(DslRegistry)` seeds it from compiled DSL.

**Test Implementations (`dsl/src/main/kotlin/cbs/dsl/impl/`):**

| Class             | Purpose                                                                                  |
|-------------------|------------------------------------------------------------------------------------------|
| `TestHelper`      | Real helper with built-in logic; `executeBlock` optional — omitted → built-in logic runs |
| `TestTransaction` | Configurable preview/execute/rollback blocks; `execute` required if no delegate          |
| `TestCondition`   | Simple predicate over `TransactionContext`                                               |

**DSL Builder Rules:**
- `EventBuilder`, `HelperBuilder`, `WorkflowBuilder` → multiple definitions per script allowed
- `TransactionBuilder`, `ConditionBuilder`, `MassOpBuilder` → **only one block per script** (`require()`)
- `HelperBuilder.hasExecuteBlock` — when `false`, `DslRunner` delegates to `ImplRegistry.resolveHelper(code)`
- `TransactionBuilder.delegateTarget` — supports delegation to another `TransactionDefinition`

**Execution Modes:**
- `STRICT` (production) — compiled Kotlin classes from `dsl` module
- `LENIENT` (dev, `@Profile("dev")`) — raw `.kts` interpreted directly via `DevDslEvaluator`

**Key Files:**
- `dsl-api/src/main/kotlin/cbs/dsl/api/` — interfaces, annotations (`@DslComponent`), context types
- `dsl/src/main/kotlin/cbs/dsl/script/` — script scope classes (`*DslScope`), `ScriptHost`, `DslCompiler`
- `dsl/src/main/kotlin/cbs/dsl/runtime/` — builders (`*Builder`), `DslRegistry`, step orchestration
- `dsl/src/main/kotlin/cbs/dsl/compiler/` — `ImportParser`, `ImportResolver`, `DslValidator`
- `dsl/src/main/kotlin/cbs/dsl/impl/` — `ImplRegistry`, `TestHelper`, `TestTransaction`, etc.
- `dsl/src/test/resources/samples/` — example `.kts` files (loan-disbursement, global, etc.)

**Dev Commands:**
```bash
./gradlew :dsl-api:build
./gradlew :dsl:build
./gradlew :dsl:test
./gradlew spotlessApply
```

**Troubleshooting:**
- `helper("X")` not found → check `ImplRegistry` seeding or `DslRegistry` compilation
- Import scope empty → verify `// #import` syntax and Pass 2 re-evaluation
- Kotlin script eval fails → check `ScriptHost.eval()` return type: `evalResult.value.returnValue.scriptInstance`
- `TestHelper` returns `"NO_PARAMS"` → called with empty params and no `executeBlock` override
