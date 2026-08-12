# Agent Guide: cbs-nova Backend

This document guides coding agents on the Java backend for the Temporal DSL Orchestration Engine. Keep it updated.
Primary architecture docs: `docs/architecture-backend.md` and `docs/dsl/`.

---

## 1. Project Map & Architecture

Operational modes:

- `run`: Executes generated workflows/activities against a Temporal cluster.
- `preview`: Fast dry-run executing `DslObject`s directly, no Temporal required.
- `explain`: Dry-run returning execution descriptions and Mermaid diagrams.

Core constructs: **Process** (Temporal Workflow), **Transaction** (Temporal Activity), **Function** (local helper), *
*Helper** (external class via SPI).

```
backend/
├── dsl-api/          # Base contracts, registries & context interfaces (zero-dep)
├── dsl/              # Runtime: Registries, Runners, Managers, Context, Result
├── dsl-codegen/      # Annotation processor generating Temporal workflows/activities
├── misc-codegen/     # SPI generator for `@Helper` classes
├── dsl-examples/     # JEP-512 compact DSL source files (no class/package/public)
├── dsl-gradle-plugin/# Standalone Gradle plugin that compiles DSL sources
├── starter/          # Spring Boot starter & REST surface (e.g. POST /api/dsl/reload)
└── temporal-example/ # Sample Temporal workflows and testing
```

**Dependency flow**: `dsl-api` (none) <- `dsl` <- `dsl-codegen` / `starter` / `dsl-examples` / `dsl-gradle-plugin`.
**Execution Layers**: Generated code -> Facade (`GlobalManager.getInstance()`) -> Managers -> Runners -> Registries.

---

## 2. Core Rules & Constraints

### DSL Authoring (Compact Sources in `dsl-examples`)

- Source files are authored without a `class`, `package`, or `public` modifier and declare exactly one
  `List<DslObject> define()` method.
- `SourceCompiler` preprocesses each compact source into a normal Java class that implements
  `cbs.nova.dsl.DslCompactSource`, validates it, and then compiles it through the standard Java compiler.
- The generated `GeneratedDslDefinitionProvider` (default package) aggregates all `define()` results via
  `java.util.ServiceLoader` so the runtime can load them.
- `DefinitionLoader` uses the same preprocessor when a configured source directory contains `.java` files; otherwise it
  loads definitions from the classpath via `ServiceLoader`.
- `dsl-gradle-plugin` provides a standalone Gradle plugin (`cbs.nova.dsl`) that compacts DSL sources. It resolves the
  compiler runtime from Maven Local using configurable `dslVersion`. See `backend/dsl-gradle-plugin/README.md`.
- **Call Hierarchy constraints**:
    - **Process** can call: Transactions, Helpers, Functions (never Processes).
    - **Transaction / Function / Helper / Compensation** can call: Helpers, Functions (never Processes/Transactions).
- All DTO inputs/outputs must be Java records annotated with `@Json` (Avaje Jsonb).
- Helper classes must implement `Executable<IN, OUT>` and be annotated with `@Helper(name = "...")` with a unique name.
- Compensation blocks may only call Helpers and Functions.

### Coding Practices & Constraints

- **Java**: Default DTOs/payloads to `record`s.
- **Nullability**: Annotate with `jspecify` annotations (`libs.jspecify`).
- **Context**: Context is immutable. Modify state using `ctx.withBody(...)` / `ctx.withMetadata(...)`.
- **GlobalManager**: Never bypass this facade or registries in generated code.
- **Do Not Edit Generated Code**: `dsl-codegen` outputs `*ProcessWorkflow`, `*ProcessDefinition`,
  `*TransactionActivity`, `*TransactionDefinition`. Edit the templates/source DSL instead.

### Code Style & Language Rules

- **Lombok**: Prefer Lombok annotations to reduce boilerplate. Use `@Getter`, `@Setter`, `@Builder`,
  `@EqualsAndHashCode`, etc. where appropriate instead of hand-written implementations.
- **Constructors**: Never write manual constructors for dependency injection or simple field assignment.
  Use `@RequiredArgsConstructor` (or `@AllArgsConstructor` when needed) from Lombok.
- **DTO conversions**: Use MapStruct for all mapping/conversion between entities, DTOs, records, and
  domain objects. Avoid manual mapping code.
- **Indentation**: 2 spaces, enforced by Spotless via `backend/gradle/code-style.gradle`.
  See `backend/gradle/eclipse-formatter.xml` for the full formatter configuration.
- **Functional style**: Prefer a functional, pipe-oriented style using the Stream API and immutable
  transformations. Favor method chaining (`stream().map(...).filter(...).collect(...)`) over
  imperative loops and mutable accumulators.
- **Clean code / small methods**: Keep implementation methods short and focused on a single responsibility.
  Extract helper methods liberally. **Keep source files under 300 lines** whenever practical; split
  large classes into focused collaborators.
- **Builder over constructor**: Prefer Lombok `@Builder` for constructing objects with multiple fields
  instead of manual constructors or long parameter lists. Use `@RequiredArgsConstructor` only for simple
  dependency injection, and never write hand-rolled constructors for DTO/value-object assembly.
- **Records over classes**: Default DTOs, payloads, and immutable value objects to Java `record`s.
  Use regular classes only when mutable state, inheritance, or complex behavior is required.
- **Functional interfaces over monolithic classes**: Use `@FunctionalInterface` for single-method
  abstractions and provide multiple small implementation classes rather than one large class that hardcodes
  many responsibilities. Split behavior into focused collaborators.

---

## 3. CLI Commands (Run from `backend/`)

```bash
./gradlew build                 # Build project and run code generation
./gradlew test                  # Run all tests
./gradlew :dsl:test             # Run specific module tests (e.g. :dsl-codegen, :starter)
./gradlew :dsl-gradle-plugin:build   # Build and validate the DSL compiler plugin
./gradlew spotlessCheck         # Check Spotless formatting rules
./gradlew spotlessApply         # Format code automatically using Spotless
```

---

## CodeGraph

> **Prerequisite**: switch to Node v22 before running CodeGraph commands:
> ```bash
> nvm use v22.20.0
> ```

The backend has its own isolated CodeGraph index under `backend/.codegraph/`.
Run all CodeGraph commands from `backend/` so only Java/Kotlin sources are indexed:

```bash
cd backend
codegraph status
codegraph query <SymbolName> --kind class --limit 5 --json
codegraph index --force   # after mass refactors
```

The frontend index (`frontend/.codegraph/`) is a separate database and must not be mixed with this one.

## 4. Key Context & Recent Changes

- **Rich Contexts**: Use sub-interfaces under `dsl-api` (`ProcessContext`, `TransactionContext`, `CompensationContext`,
  `FunctionContext<T>`).
- **Fluent APIs**: `ProcessContext.complete(Object)` returns result; `CompensationContext.log()` returns
  `CompensationContext<T>`.
- **Result Casts**: Use `Result.as(Class)` and `Result.asMap()` on `Result`.
- **Parameter DSL**: Support untyped parameters via `.parameters(...)`, `ParameterRegistry`, and `MapInput.of(k, v)`.
- **Heartbeat**: Configured via `TransactionBuilder.heartbeatTimeout(Duration)`.
- **SPI Discovery**: `misc-codegen` generates a `HelperResolver` implementation and a
  `META-INF/services/cbs.nova.dsl.HelperResolver` descriptor for each `@Helper` class. On startup
  `DslAutoConfiguration` loads these resolvers via `ServiceLoader` and registers helpers through
  `GlobalManager`. Runtime reflection scanning of `dsl.helper-scan-packages` is removed.
- **CodeGraph**: Prioritize `codegraph_*` tools for fast structural/symbol exploration over slow grep.

---

## 5. Agent Workflows

- **Adding a DSL construct**: Update `dsl/` API, `dsl-codegen/` templates/validation, add tests, update docs.
- **Adding a runtime feature**: Maintain `GlobalManager` facade, add JUnit tests, test all three modes (`run`,
  `preview`, `explain`).
- **Adding a Temporal example**: Put under `temporal-example/` and test using `TestWorkflowEnvironment`.

---

## 6. Onboarding Reading List

1. `docs/architecture-backend.md` (overview)
2. `docs/dsl/constructs.md` (execution contracts)
3. `docs/dsl/authoring.md` (writing DSL flows)
4. `docs/dsl/codegen.md` (generated code conventions)
5. `docs/dsl/runtime.md` (registries, runners, managers)
