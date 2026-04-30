# DSL Knowledge — CBS Nova

**Stack:** Java DSL, Java Annotation Processing (APT), Java 25, Gradle. Modules: `dsl-api` (shared API), `dsl`
(runtime + compiler), `dsl-codegen` (annotation processor).

**DSL File Types & Conventions:**

| Suffix / Class Name      | Scope Class             | Builder              | Defines                                                    |
|--------------------------|-------------------------|----------------------|------------------------------------------------------------|
| `*Event.java`            | `EventDslScope`         | `EventBuilder`       | parameters, context, display, transactions, finish         |
| `*Transaction.java`      | `TransactionDslScope`   | `TransactionBuilder` | parameters, context, preview, execute, rollback            |
| `*.helper.java`          | `HelperDslScope`        | `HelperBuilder`      | parameters, context, execute                               |
| `*Condition.java`        | `ConditionDslScope`     | `ConditionBuilder`   | parameters, context, predicate                             |
| `*Workflow.java`         | `WorkflowDslScope`      | `WorkflowBuilder`    | states, initial, terminal, transitions                     |
| `*MassOperation.java`    | `MassOperationDslScope` | `MassOpBuilder`      | parameters, triggers, source, lock, context, item, signals |

One folder per event/workflow. Mass ops live under `mass-operations/`. Helpers can be global or event-scoped.

**Two-Pass Compilation:**

1. Pass 1 — parse all `.java` DSL files without import resolution, build merged `DslRegistry`
2. Pass 2 — resolve Java imports against merged `DslRegistry`, validate all references

**Import System:**
```java
import loan.disbursement.*;
import global.BankingHelpers;
import code.cbs.dsl.impl.TestHelpers;   // code-based import
```

`framework.*` imports are standard Java imports. The `DslCompiler` resolves import declarations against
the merged `DslRegistry`.

**Context Hierarchy:**
```
BaseContext(eventCode, workflowExecutionId, performedBy, dslVersion)
  └── ParameterContext(eventParameters)
        └── EnrichmentContext(enrichment: MutableMap)  ← put(key, value)
              ├── HelperContext(params)                 ← helper(name, params): Object
              ├── TransactionContext(isResumed)         ← prolong(Action), delegate()
              └── FinishContext(displayData)
```

**Registries:**

- `DslRegistry` — immutable-read, compilation-time registry of all definitions from `.java` DSL files. Strict: throws on
  duplicate code.
- `ImplRegistry` — runtime dispatch registry. Lookup priority: **name first, then code**. Last-write-wins (supports test
  overrides). `populateFrom(DslRegistry)` seeds it from compiled DSL.

**Test Implementations (`dsl/src/main/java/cbs/dsl/impl/`):**

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
- `GENERATED` (production) — `dsl-codegen` annotation processor generates Temporal workflow/activity classes from DSL definitions
- `REFLECTED` (dev, `@Profile("dev")`) — raw `.java` DSL executed via reflection through generic `ReflectiveWorkflow` / `ReflectiveActivity` wrappers

**Key Files:**
- `dsl-api/src/main/java/cbs/dsl/api/` — interfaces, annotations (`@DslComponent`), context types
- `dsl/src/main/java/cbs/dsl/script/` — DSL scope classes (`*DslScope`), `DslCompiler`, `DslParser`
- `dsl/src/main/java/cbs/dsl/runtime/` — builders (`*Builder`), `DslRegistry`, step orchestration
- `dsl/src/main/java/cbs/dsl/compiler/` — `ImportResolver`, `DslValidator`
- `dsl/src/main/java/cbs/dsl/impl/` — `ImplRegistry`, `TestHelper`, `TestTransaction`, etc.
- `dsl/src/test/resources/samples/` — example `.java` DSL files (loan-disbursement, global, etc.)
- `dsl-codegen/src/main/java/cbs/dsl/codegen/` — Java APT annotation processor, `GeneratedImplRegistrations` generator, Temporal code generator

**Dev Commands:**
```bash
./gradlew :dsl-api:build
./gradlew :dsl:build
./gradlew :dsl:test
./gradlew spotlessApply
```

**Troubleshooting:**
- `helper("X")` not found → check `ImplRegistry` seeding or `DslRegistry` compilation
- Import scope empty → verify Java import syntax and Pass 2 resolution
- Java DSL parse fails → check `DslCompiler` return type: parsed definitions must return valid `DslDefinition`
- `TestHelper` returns `"NO_PARAMS"` → called with empty params and no `executeBlock` override

> More info can be found here [dsl-design.md](arch/dsl-design.md)
