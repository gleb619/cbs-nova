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
├── dsl-api/              # Base contracts, registries & context interfaces (zero dep)
├── dsl/                  # Runtime: registries, runners, managers, context, result
├── dsl-codegen/          # Annotation processor → Temporal workflows/activities
├── dsl-examples/         # Compact DSL source files (process/transaction examples)
├── starter/              # Spring Boot starter, REST surface, built-in @Helpers
├── dsl-gradle-plugin/    # Standalone Gradle plugin for DSL compilation
├── misc-codegen/         # SPI generator for @Helper classes
└── dsl-idea-plugin/      # IntelliJ IDEA plugin (experimental)

frontend/
├── admin-ui-plugin/      # Nuxt module — mounts the admin UI + Nitro BFF
└── components/           # Shared Vue 3 + Vite component library & composables

docs/
├── architecture-backend.md   # Backend design & runtime modes
├── architecture-ui.md        # Frontend/BFF architecture
├── dsl/                      # DSL authoring & codegen guides
├── kanban.md                 # Task board (source of truth for current state)
├── loop.md                   # Autonomous development loop prompt
└── plans/                    # Detailed plan files per task (<ID>-*.md)
```

Backend modules are declared in `backend/settings.gradle:25-32`. All modules are
cross-referenced in [`backend/AGENTS.md`](backend/AGENTS.md#1-project-map--architecture).

---

## Prerequisites

| Tool       | Version                      | Notes                                            |
|------------|------------------------------|--------------------------------------------------|
| **JDK**    | 25 (see note)                | Defined in `backend/gradle/libs.versions.toml:2` |
| **pnpm**   | 9.x                          | Package manager (`frontend/package.json:7`)      |
| **Docker** | 24+ with Compose v2          | For the full stack (Postgres, Keycloak, etc.)    |

> Java 25 is the version used in CI. Any JDK 21+ supported by Spring Boot will
> work for local development. See [`DEVELOPING.md`](DEVELOPING.md) for
> per-platform install instructions.

---

## Build & test

### Backend

Run all commands from `backend/`.

```bash
./gradlew build                    # build + code generation
./gradlew test                     # all module tests
./gradlew spotlessApply            # format code (required before commit)
```

Per module:

```bash
./gradlew :dsl-api:test            # base contracts
./gradlew :dsl:test                # runtime
./gradlew :dsl-codegen:test        # code generation
./gradlew :dsl-examples:build      # DSL examples (compile-validated)
./gradlew :starter:test            # Spring Boot starter
./gradlew :starter:bootRun         # run the backend server (hot-reload)
./gradlew :dsl-gradle-plugin:test  # Gradle plugin
./gradlew :misc-codegen:test       # SPI generator
```

The full module list is in `backend/settings.gradle`; each module name maps to a
`:`-prefixed Gradle path.

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

Built-in helpers live in `backend/starter/src/main/java/cbs/nova/starter/helpers/`.
Each helper follows the same template.

### 1. Input/Output records

Create the In/Out records under `helpers/model/`:

```java
// backend/starter/src/main/java/cbs/nova/starter/helpers/model/MyHelperIn.java
public record MyHelperIn(String value) {}
```

```java
// backend/starter/src/main/java/cbs/nova/starter/helpers/model/MyHelperOut.java
public record MyHelperOut(String result) {}
```

### 2. Helper class

Place the helper in `helpers/`:

```java
package cbs.nova.starter.helper;

import cbs.nova.dsl.*;
import cbs.nova.starter.helper.model.MyHelperIn;
import cbs.nova.starter.helper.model.MyHelperOut;

@Helper(name = "myHelper")
public class MyHelperHelper implements Executable<MyHelperIn, MyHelperOut> {

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

Place the test under `src/test/java/cbs/nova/starter/helpers/`:

```java
class MyHelperHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final MyHelperHelper helper = new MyHelperHelper();

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
- [`FilterRecordsHelper.java`](backend/starter/src/main/java/cbs/nova/starter/helpers/FilterRecordsHelper.java)
- [`SortRecordsHelper.java`](backend/starter/src/main/java/cbs/nova/starter/helpers/SortRecordsHelper.java)
- [`JsonExtractHelper.java`](backend/starter/src/main/java/cbs/nova/starter/helpers/JsonExtractHelper.java)
- In/Out model records in [`helpers/model/`](backend/starter/src/main/java/cbs/nova/starter/helpers/model/)
- Test examples in [`src/test/java/cbs/nova/starter/helpers/`](backend/starter/src/test/java/cbs/nova/starter/helpers/)

### 4. Verify

```bash
./gradlew :starter:test
```

The helper is auto-registered at runtime — no manual SPI wiring needed.

---

## Adding a DSL example

DSL examples are compact source files under
`backend/dsl-examples/src/dsl/`. Each file declares a single
`List<DslObject> define()` method without a class or package declaration.

### 1. Create a compact source file

```java
// backend/dsl-examples/src/dsl/MyExampleDsl.java
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
`backend/dsl-examples/src/models/` in the package
`cbs.nova.dslexamples`.

### 3. Verify

```bash
./gradlew :dsl-examples:build
```

The `dsl-gradle-plugin` compiles compact sources, aggregates them via
`ServiceLoader`, and validates them at build time. No manual registration is
required.

Real examples to reference:
- [`HelperPipelineDsl.java`](backend/dsl-examples/src/dsl/HelperPipelineDsl.java)
  (helper composition pipeline, added in T126)
- [`OrderSagaDsl.java`](backend/dsl-examples/src/dsl/OrderSagaDsl.java) (saga
  with compensation)
- [`SimpleGreetingDsl.java`](backend/dsl-examples/src/dsl/SimpleGreetingDsl.java)

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

3. **Autonomous execution loop** — [`docs/loop.md`](docs/loop.md) is the prompt
   for the `/loop` agent. It reads the kanban, picks the next `Ready` task,
   delegates code writing to subagents, and verifies the result.

4. **Idea factory** — refer to `docs/loop.md#state-next_batch` for how the loop
   generates new tasks from the architecture roadmaps.

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
