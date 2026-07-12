# CBS Nova — System Architecture

CBS Nova is a **declarative Temporal DSL orchestration engine** with an integrated administrative web interface. Its
primary goal is to let teams author, preview, and run durable business workflows in Java without writing Temporal
boilerplate, while operating the system through a single Vue/Nuxt admin UI.

## Main goal of the application

Give non-developers and developers a shared, lightweight authoring surface for distributed orchestrations, and give
operations a unified web interface to manage, observe, and integrate those orchestrations.

The backend turns compact Java DSL definitions into versioned Temporal workflows and activities. The frontend exposes
those definitions, executions, and operational controls through an admin interface that proxies all backend calls
through a server-side BFF.

## Core backend

- **DSL authoring** — compact Java source files (`dsl-examples/src/`) define `Process`, `Transaction`, `Function`, and
  `Helper` constructs using a fluent builder API.
- **Compile-time generation** — a Gradle DSL module scans DSL sources, builds an AST, validates semantics, and
  generates Temporal `@WorkflowInterface` and `@ActivityInterface` classes.
- **Runtime engine** — layered as Registry → Runner → Manager → `GlobalManager` facade. Generated code only calls
  `GlobalManager`.
- **Operational modes** — Run (live Temporal), Preview (dry-run DSL execution), and Explain (preview plus Mermaid
  description and human-readable text).
- **Compensation & observability** — declarative rollback/cleanup steps and execution tracing through the DSL runtime.

See [Backend architecture](architecture-backend.md) for the full backend design, runtime contract, and implementation
roadmap.

## Core frontend

- **`frontend/admin-ui-plugin`** — Nuxt module that mounts the full admin UI into any host Nuxt app. Includes Pinia state, Tailwind styling, and a Nitro-based TypeScript backend-for-frontend (BFF).
- **`frontend/components`** — standalone Vue 3 + Vite component library exporting reusable SFCs, composables, and the
  canonical Tailwind color theme from `docs/colors.md`.
- **Security pattern** — the browser never calls Spring Boot directly. The BFF holds and refreshes the JWT, then forwards
  authenticated requests to the backend.

See [UI architecture](architecture-ui.md) for the frontend layout, communication pattern, and build commands.

## How the tiers connect

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              Author / Operator                                       │
│                  writes DSL in Java            uses admin UI in browser              │
└──────────────┬───────────────────────────────────────┬──────────────────────────────┘
               │                                       │
               ▼                                       ▼
┌──────────────────────────────────┐      ┌──────────────────────────────────────────┐
│  DSL Module (`dsl-examples/src/`) │      │  @cbs/admin-ui-plugin (Nuxt module)      │
│  compact Java definitions         │      │  • Vue pages / components                 │
│                                   │      │  • Pinia stores                           │
└──────────────┬────────────────────┘      │  • Nitro server/ BFF routes               │
               │                          └────────────────────┬─────────────────────┘
               │                                             │ HTTP + JWT
               ▼                                             ▼
┌──────────────────────────────────┐      ┌──────────────────────────────────────────┐
│  Gradle DSL compiler             │      │  Spring Boot API                         │
│  generates Temporal classes        │─────▶│  • REST endpoints                         │
│                                  │      │  • DSL runtime / worker bootstrap         │
└──────────────────────────────────┘      │  • Temporal client/worker                 │
                                            └──────────────────────────────────────────┘
                                                          │
                                                          ▼
                                            ┌──────────────────────────────────────────┐
                                            │  Temporal Server                           │
                                            │  durable workflow executions               │
                                            └──────────────────────────────────────────┘
```

## Key design principles

- **Business autonomy** — DSL authors modify flows without touching generated Temporal code.
- **Correctness** — every workflow instance pins to the DSL version it started with.
- **Single-source UI theme** — `frontend/components` owns the Tailwind palette.
- **Server-side auth** — JWTs stay in the BFF; the browser only holds a session cookie.
- **Layer isolation** — backend generated code uses only the `GlobalManager` facade.

## Where to read more

- [Backend architecture](architecture-backend.md) — Java / Temporal backend details
- [UI architecture](architecture-ui.md) — Vue/Nuxt admin interface details
- [DSL constructs](dsl/constructs.md), [authoring](dsl/authoring.md), [codegen](dsl/codegen.md), [runtime](dsl/runtime.md)
- [Colors](colors.md) — brand palette used across the UI
- [Development loop](loop.md) — how the autonomous build loop picks up backend and frontend tasks
