# Agent Guide for `cbs-nova` Backend

This file is for coding agents (Claude/Codex/Cursor/etc.) working on the **Java backend** of the Temporal DSL
Orchestration Engine. Read it before you edit code, and keep it in sync with the project as it evolves.

Primary source of truth for architecture: `docs/architecture.md`. Deep-dive docs live in `docs/dsl/`.

---

## 1. Project at a glance

We are building a **declarative Java DSL for Temporal workflows and activities**. Business flows are written as
compact DSL definitions; a custom code generator turns them into production Temporal classes at build time.

Three operational modes share the same definitions:

| Mode      | What it does                                                       | Temporal cluster needed? |
|-----------|--------------------------------------------------------------------|--------------------------|
| `run`     | Executes generated workflow/activity classes against Temporal      | Yes                      |
| `preview` | Executes `DslObject`s directly, without Temporal (fast dry-run)    | No                       |
| `explain` | Preview mode that also returns a description and a Mermaid diagram | No                       |

Core constructs: **Process** → Temporal Workflow, **Transaction** → Temporal Activity, **Function** → local DSL
helper, **Helper** → normal Java class reusable from DSL.

---

## 2. Module map (`backend/`)

```
backend/
├── dsl-api/             # Core DSL contracts and shared helpers/info about core project objects (zero dependencies)
├── dsl/                 # DSL API: Dsl, DslObject, Executable, Context, Result, @Helper, registries
├── dsl-codegen/         # Annotation processor/code generator: scans DSL sources, validates, emits Temporal classes
├── starter/             # Spring Boot starter: TemporalConfiguration, AutoConfiguration, REST surface
├── temporal-example/    # Standalone Temporal examples (greeting, booking, aggregation) and tests
└── gradle/              # Shared build scripts and libs.versions.toml
```

Key Gradle files:

- `backend/settings.gradle` — defines subprojects `dsl-api`, `dsl`, `dsl-codegen`, `starter`, `temporal-example`
- `backend/build.gradle` — root project configuration
- `backend/gradle/java.gradle` — Java toolchain (UTF-8)
- `backend/gradle/test.gradle` — JUnit 5 + test-logger
- `backend/gradle/code-style.gradle` — Spotless with Eclipse formatter
- `backend/gradle/libs.versions.toml` — version catalog

Dependency direction: `dsl-api` is the base layer (no other backend modules depend on it, and it depends on none).
`dsl` depends on `dsl-api`; `dsl-codegen` depends on `dsl` and `dsl-api`; `starter` depends on `dsl-api`, `dsl`,
and `dsl-codegen` (annotation processor); `temporal-example` is independent sample code.

---

## 3. Build, test, and style commands

Always run from `backend/`:

```bash
# Build everything
./gradlew build

# Run tests for all modules
./gradlew test

# Run tests for a single module
./gradlew :dsl:test
./gradlew :dsl-codegen:test
./gradlew :starter:test
./gradlew :temporal-example:test

# Check code style (Spotless)
./gradlew spotlessCheck

# Auto-format code
./gradlew spotlessApply
```

Do not commit code that fails `spotlessCheck` or `./gradlew build`. CI will reject it.

---

## 4. Code style & conventions

- **Java version**: set in `libs.versions.toml` via `libs.versions.java`.
- **Indentation**: 2 spaces (enforced by Spotless).
- **Imports order**: blank, `javax`, `java` (enforced by Spotless).
- **Encoding**: UTF-8.
- **Nullability**: prefer `jspecify` annotations; the project pulls in `libs.jspecify`.
- **Records** are the default for DTOs/input/output payloads.
- **JSON binding**: annotate records with `@Json` (Avaje Jsonb). Use reflection-free generated adapters.
- Do not add new dependencies without updating `libs.versions.toml` and justifying why.

---

## 5. DSL authoring rules

DSL definitions live in a dedicated Gradle module (not the current backend modules yet — see roadmap). When you author or
touch DSL code, obey these rules:

1. **Source-file format**: JEP-512 compact source.
   - No `class` declaration.
   - No `public` modifier.
   - No `package` statement.
   - Expose one `List<DslObject> define()` method.

2. **Construct scoping** (hard constraints):

   | Caller        | Can call Processes | Can call Transactions | Can call Helpers | Can call Functions |
   |---------------|--------------------|-----------------------|------------------|--------------------|
   | Process       | no                 | yes                   | yes              | yes                |
   | Transaction   | no                 | no                    | yes              | yes                |
   | Function      | no                 | no                    | yes              | yes                |
   | Helper        | no                 | no                    | yes              | yes                |
   | Compensation  | no                 | no                    | yes              | yes                |

3. **Input/output records** must be annotated with `@Json` for Avaje Jsonb.
4. **Helper classes** must implement `Executable<IN, OUT>` and be annotated with `@Helper(name = "...")`. The name
   must be unique across helpers AND DSL functions.
5. **Functions** are lightweight local helpers; they never generate Temporal classes and never support compensation.
6. **Compensation** is allowed on Processes and Transactions. Compensation blocks may only call Helpers/Functions.

See `docs/dsl/authoring.md` and `docs/dsl/constructs.md` for full examples.

---

## 6. Runtime concepts agents must respect

The runtime is layered. Generated code talks only to `GlobalManager.getInstance()`; do not wire registries or runners
directly in generated code.

```
DSL definitions
      ↓
Registry layer   → ProcessRegistry, TransactionRegistry, HelperRegistry
Runner layer     → ProcessRunner, TransactionRunner, HelperRunner
Manager layer    → ProcessManager, TransactionManager, HelperManager
Facade           → GlobalManager.getInstance()
```

- **Registries** store definitions by name. `HelperRegistry` holds both `@Helper` classes and DSL `Function`s.
- **Runners** execute a definition against a typed `Context` (`preview` / `execute` / `explain`).
- **Managers** select the execution path: real Temporal stubs in `run` mode, direct execution in `preview`/`explain`.
- **Context** is immutable. Use `ctx.withBody(...)` / `ctx.withMetadata(...)` to transform it.
- **Result** type is the idiomatic way to represent success/failure inside DSL definitions.

When you change runtime code, preserve this facade contract. Generated code depends on it.

---

## 7. Code generation boundaries

`dsl-codegen` is an annotation processor. It:

1. Scans DSL module `/src` for `define()` methods.
2. Builds descriptors: `ProcessDescriptor`, `TransactionDescriptor`, `FunctionDescriptor`.
3. Validates semantics (parameters, helper refs, cycles).
4. Generates Temporal classes annotated with `@WorkflowInterface`, `@WorkflowMethod`, `@ActivityInterface`, `@ActivityMethod`.

Generated artifacts (conventions):

- `*ProcessWorkflow` — workflow interface.
- `*ProcessDefinition` — workflow implementation.
- `*TransactionActivity` — activity interface.
- `*TransactionDefinition` — activity implementation.

Never hand-edit generated classes. If generated code is wrong, fix the generator or the DSL source, not the output.

---

## 8. Common agent tasks and how to handle them

### Adding a new DSL construct

- Update `dsl/` builder API and runtime model first.
- Update `dsl-codegen/` descriptor, validation, and templates.
- Add tests in both `dsl/` and `dsl-codegen/`.
- Update this guide and `docs/dsl/` docs.

### Adding a new runtime feature (registry/runner/manager)

- Keep the three-layer architecture intact.
- Maintain `GlobalManager` as the single facade.
- Add unit tests with JUnit 5; use AssertJ where helpful.
- If behavior differs across `run`/`preview`/`explain`, test all three.

### Adding REST endpoints

Endpoints are mode-agnostic. The controller injects a `DslRuntime` bean; profile-specific implementations choose between
`DevDslRuntime` (preview/explain) and `ProductionDslRuntime` (run). Do not put mode logic in controllers.

### Adding a Temporal example

Use `temporal-example/` for self-contained workflow/activity demonstrations. Keep examples grouped by complexity (`simple/`,
`medium/`, `complex/`). Each example should have JUnit tests using `temporal-testing`.

### Fixing tests

- Prefer targeted unit tests over broad integration tests.
- Use `TestWorkflowEnvironment` from `temporal-testing` for workflow/activity tests.
- Keep tests deterministic; avoid real sleep.

---

## 9. What NOT to do

- Do not add business logic inside generated Temporal classes — put it in Helpers, Functions, or the DSL definition.
- Do not call Processes or Transactions from within Functions, Helpers, or compensation blocks.
- Do not mutate `Context` in place; it is immutable.
- Do not bypass `GlobalManager` from generated code.
- Do not hand-edit generated sources.
- Do not add runtime dependencies to `dsl/` unless they are truly API-level.
- Do not break the `run`/`preview`/`explain` symmetry without documenting why.

---

## 10. Reading list for agents

Read these in order when onboarding:

1. `docs/architecture.md` — high-level system overview.
2. `docs/dsl/constructs.md` — execution contract and the four constructs.
3. `docs/dsl/authoring.md` — how to write DSL flows.
4. `docs/dsl/codegen.md` — generated code conventions.
5. `docs/dsl/runtime.md` — registries, runners, managers, modes, REST surface.

Then explore the actual code:

- `backend/dsl/src/main/java/...`
- `backend/dsl-codegen/src/main/java/...`
- `backend/starter/src/main/java/...`
- `backend/temporal-example/src/main/java/...`

---

## 11. When in doubt

- Re-read the relevant `docs/dsl/*.md` file.
- Run `./gradlew build` and `./gradlew test` before declaring work done.
- If a change affects generated code, run the code generator and inspect its output.
- Keep the DSL author's mental model simple: they write compact Java; we generate Temporal.
