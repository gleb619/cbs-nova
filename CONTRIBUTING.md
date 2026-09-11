# Contributing

Thanks for your interest in cbs-nova. This guide covers the project layout,
prerequisites, build/test workflow, how to add a built-in `@Helper` or a DSL
example, the kanban task workflow, and the commit convention.

> **First read**: [`README.md`](README.md) for the project front door and
> quickstart. `AGENTS.md` at root routes you to
> [`backend/AGENTS.md`](backend/AGENTS.md) (Java backend) or
> [`frontend/AGENTS.md`](frontend/AGENTS.md) (Vue/Nuxt frontend) for deep
> coding-convention detail.

---

## Project layout

```
backend/
├── dsl-platform/         # Parent build for the DSL platform
│   ├── dsl-api/          # Base contracts, registries & context interfaces (zero dep)
│   ├── dsl/              # Runtime: registries, runners, managers, context, result
│   ├── dsl-codegen/      # Annotation processor → Temporal workflows/activities
│   └── misc-codegen/     # SPI generator for @Helper classes
├── dsl-plugins/          # Parent build for tooling plugins
│   ├── dsl-gradle-plugin/    # Standalone Gradle plugin for DSL compilation
│   ├── dsl-idea-plugin/      # IntelliJ IDEA plugin (experimental)
│   └── dsl-builder/          # Spring Boot service: on-demand DSL compilation via Tooling API + JGit
└── dsl-starter/          # Parent build for runtime + examples
    ├── dsl-examples/     # Compact DSL source files (process/transaction examples)
    ├── starter/          # Spring Boot starter, REST surface, built-in @Helpers
    └── starter-launcher/ # Example Spring Boot host for the starter

frontend/
├── admin-ui-plugin/      # Nuxt module — mounts the admin UI + Nitro BFF
└── components/           # Shared Vue 3 + Vite component library & composables

docs/
├── architecture-backend.md   # Backend design & runtime modes
├── architecture-ui.md        # Frontend/BFF architecture
├── dsl/                      # DSL authoring & codegen guides
├── kanban.md                 # Task board (source of truth for current state)
├── loop.implement.md         # Autonomous implementation loop prompt (executes Ready tasks)
├── loop.plan.md              # Autonomous planning loop prompt (generates/promotes tasks)
└── plans/                    # Detailed plan files per task (<ID>-*.md)
```

Backend modules live in three independent Gradle sub-builds (`dsl-platform`, `dsl-plugins`,
`dsl-starter`), coordinated by the root `backend/build.gradle` delegate tasks. All modules are
cross-referenced in [`backend/AGENTS.md`](backend/AGENTS.md#1-project-map--architecture).

---

## Prerequisites

| Tool       | Version                              | Why                                       |
|------------|--------------------------------------|-------------------------------------------|
| **JDK**    | 25 (or 21+ supported by Spring Boot) | Backend Gradle / Spring Boot starter       |
| **pnpm**   | 9.x                                  | Frontend (Nuxt admin-ui-plugin workspace) |
| **Docker** | 24+ with Compose v2                  | Local infra (Postgres / Keycloak / Temporal) |

Per-platform install commands (apt / brew), service ports, and credentials live
in [`DEVELOPING.md`](DEVELOPING.md). JDK 25 is the CI matrix default — locally
any 21+ Spring Boot supports works through `make backend`, which handles the
Gradle wrapper selection for you.

---

## Build & test

### Backend

Run all commands from `backend/`. `./gradlew build` / `./gradlew test` delegate to the
three sub-builds in order (platform → plugins → starter).

```bash
./gradlew build                    # build + code generation (all sub-builds)
./gradlew test                     # all module tests
./gradlew spotlessApply            # format code (required before commit)
```

Per module (each sub-build has its own Gradle wrapper invocation):

```bash
./gradlew -p dsl-platform :dsl-api:test        # base contracts
./gradlew -p dsl-platform :dsl:test            # runtime
./gradlew -p dsl-platform :dsl-codegen:test    # code generation
./gradlew -p dsl-starter :dsl-examples:build   # DSL examples (compile-validated)
./gradlew -p dsl-starter :starter:test         # Spring Boot starter
./gradlew -p dsl-starter :starter:bootRun      # run the backend server (hot-reload)
./gradlew -p dsl-plugins :dsl-gradle-plugin:test  # Gradle plugin
./gradlew -p dsl-plugins :dsl-builder:test        # DSL builder service
./gradlew -p dsl-platform :misc-codegen:test   # SPI generator
```

Each module name maps to its directory under `dsl-platform/`, `dsl-plugins/`, or
`dsl-starter/` and is invoked with `-p <sub-build> :<module>:<task>`.

> **BootRun caveats** — easy footguns when running the backend on the host:
>
> - **Port**: Spring Boot defaults to `8080`. The Nuxt BFF and `make backend`
>   both expect **`8090`**. Either set `SERVER_PORT=8090` before `bootRun`, or
>   use `make backend` (which sets it for you). See
>   [`DEVELOPING.md`](DEVELOPING.md#services-ports-and-default-credentials) for
>   the canonical ports table.
> - **Gradle wrapper**: the repo-root `./gradlew` is Gradle 8.13 and fails
>   under JDK 25. The per-sub-build wrappers (`backend/dsl-platform/gradlew`,
>   `backend/dsl-starter/gradlew`) target Gradle 9.4.1 and are what the commands
>   above resolve to. If you see "Unsupported class file major version" or
>   "JAVA_HOME not set" you're hitting the wrong wrapper.

### Frontend

Run all commands from `frontend/`.

```bash
pnpm install                       # install workspace dependencies
pnpm dev                           # start admin-ui-plugin dev server
pnpm test                          # run admin-ui-plugin tests
pnpm check:fix                     # lint + format fix (Biome)
```

Package-specific:

```bash
pnpm --filter @cbs/admin-ui-plugin test    # admin UI plugin tests
pnpm --filter components test              # component library tests
pnpm --filter components build             # build component library
```

Scripts are defined in `frontend/package.json` (workspace root),
`frontend/admin-ui-plugin/package.json`, and `frontend/components/package.json`.

---

## Adding a built-in `@Helper`

Built-in helpers live in `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/` (singular).
Each helper follows the same template. The directory name follows the convention seen in
shipped helpers (`BackoffHelper.java`, `Base64Helper.java`, `CompressionHelper.java`, ...).

### 1. Input/Output records

Create the In/Out records under `helper/model/`:

```java
// backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/model/MyHelperIn.java
public record MyHelperIn(String value) {}
```

```java
// backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/model/MyHelperOut.java
public record MyHelperOut(String result) {}
```

### 2. Helper class

Place the helper in `helper/`:

```java
package cbs.nova.starter.helper;

import cbs.nova.dsl.*;
import cbs.nova.starter.helper.model.MyHelperIn;
import cbs.nova.starter.helper.model.MyHelperOut;

@Helper(name = "myHelper")
public class MyHelper implements Executable<MyHelperIn, MyHelperOut> {

  @Override
  public @NonNull Result<MyHelperOut> execute(@NonNull Context<MyHelperIn> ctx) {
    MyHelperIn input = ctx.body();
    // ... logic ...
    return Result.success(new MyHelperOut("done"));
  }
}
```

The `@Helper(name = "...")` annotation must have a unique name — `misc-codegen`
discovers it at compile time via annotation processing and registers it in the
SPI descriptor consumed by `DslAutoConfiguration`.

### 3. Unit test

Place the test under `src/test/java/cbs/nova/starter/helper/`:

```java
class MyHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final MyHelper helper = new MyHelper();

  @Test
  void doesSomething() {
    var ctx = contextFactory.of(new MyHelperIn("hello"), ExecutionMode.PREVIEW);
    Result<MyHelperOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("done");
  }
}
```

Real examples:
- [`FilterRecordsHelper.java`](backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/FilterRecordsHelper.java)
- [`SortRecordsHelper.java`](backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/SortRecordsHelper.java)
- [`JsonExtractHelper.java`](backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/JsonExtractHelper.java)
- In/Out model records in [`helper/model/`](backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/model/)
- Test examples in [`src/test/java/cbs/nova/starter/helper/`](backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/)

### 4. Verify

```bash
./gradlew :starter:test
```

The helper is auto-registered at runtime — no manual SPI wiring needed.

---

## Adding a DSL example

DSL examples are compact source files under
`backend/dsl-starter/dsl-examples/src/dsl/`. Each file declares a single
`List<DslObject> define()` method without a class or package declaration.

### 1. Create a compact source file

```java
// backend/dsl-starter/dsl-examples/src/dsl/MyExampleDsl.java
import ...;

List<DslObject> define() {
  return Dsl.process("MyExample")
      .input(MyIn.class)
      .output(MyOut.class)
      .execute(ctx -> {
        // process logic
        return Result.success(new MyOut("done"));
      })
      .buildList();
}
```

### 2. Register input/output model records

If the example defines custom In/Out records, place them under
`backend/dsl-starter/dsl-examples/src/models/` in the package
`cbs.nova.dslexamples`.

### 3. Verify

```bash
./gradlew :dsl-examples:build
```

The `dsl-gradle-plugin` compiles compact sources, aggregates them via
`ServiceLoader`, and validates them at build time. No manual registration is
required. The same compilation is also exposed as an on-demand REST service by
`dsl-plugins/dsl-builder` (no local Gradle setup needed) — see
[`backend/dsl-plugins/dsl-builder/README.md`](backend/dsl-plugins/dsl-builder/README.md).

Real examples to reference:
- [`HelperPipelineDsl.java`](backend/dsl-starter/dsl-examples/src/dsl/HelperPipelineDsl.java)
  (helper composition pipeline, added in T126)
- [`OrderSagaDsl.java`](backend/dsl-starter/dsl-examples/src/dsl/OrderSagaDsl.java) (saga
  with compensation)
- [`SimpleGreetingDsl.java`](backend/dsl-starter/dsl-examples/src/dsl/SimpleGreetingDsl.java)

---

## Workflow: kanban & loop

This repo uses a lightweight kanban workflow managed through the coding agent:

1. **Task board** — [`docs/kanban.md`](docs/kanban.md) tracks all tasks with
   status (`Backlog` / `Ready` / `In Progress` / `Done`), priority, and links to
   the detailed plan file.

2. **Plan files** — every task has a plan file under
   [`docs/plans/`](docs/plans/) following the naming convention
   `<ID>-short-title.md` (e.g. `T133-contributing-guide.md`). Plans include
   goal, acceptance criteria, files to modify, and verification commands.

3. **Autonomous implementation loop** — [`docs/loop.implement.md`](docs/loop.implement.md)
   is the prompt for the `/loop` agent. It reads the kanban, picks the next
   `Ready` task, delegates code writing to subagents, and verifies the result.
   It never plans — it only implements tasks already promoted to `Ready`.

4. **Idea factory / planning loop** — [`docs/loop.plan.md`](docs/loop.plan.md)
   generates new tasks from the architecture roadmaps, promotes `Backlog` to
   `Ready`, and writes the plan files `loop.implement.md` consumes.

---

## Commit convention

| Scope | Format | Example |
|-------|--------|---------|
| Kanban changes | `feat(kanban): add <ID> — <title>` | `feat(kanban): add T133 — CONTRIBUTING.md developer guide` |
| Backend feature | `feat(<scope>): <message>` | `feat(starter): add SortRecordsHelper` |
| Frontend feature | `feat(<scope>): <message>` | `feat(admin-ui): add reload button` |
| Bug fix | `fix(<scope>): <message>` | `fix(dsl-codegen): correct import ordering` |
| Tests | `test(<scope>): <message>` | `test(dsl): add ContextFactory unit tests` |
| Docs | `docs: <message>` | `docs: update architecture overview` |

- Follow [Conventional Commits](https://www.conventionalcommits.org/) for
  non-kanban changes.
- No `Co-Authored-By` trailers (the repo git account is the single author).
- Backend commits must pass `./gradlew spotlessCheck`; frontend commits must
  pass `pnpm --filter @cbs/admin-ui-plugin lint`.
