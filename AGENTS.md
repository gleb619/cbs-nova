# cbs-nova — Agent Guide

cbs-nova is a Temporal DSL Orchestration Engine with a Java backend and a Vue/Nuxt admin frontend.

- Backend: declarative Java DSL that compiles to Temporal workflows/activities. Lives in `backend/`.
- Frontend: `admin-ui-plugin` — a Nuxt module that mounts the full admin UI into any host Nuxt app. Includes a Nitro BFF that proxies to Spring Boot. Lives in `frontend/`.

## Task routing

- If your task is about the Java backend, DSL, code generation, Temporal workers, or Spring Boot API: read
  `backend/AGENTS.md` first.
- If your task is about the Vue/Nuxt UI, Tailwind styling, Pinia stores, or BFF routes: read 
  `frontend/AGENTS.md` first.

## Architecture docs

- `docs/architecture-backend.md` — backend design and runtime modes.
- `docs/architecture-ui.md` — frontend/BFF architecture.

## Quick end-to-end check

1. Start Postgres + Temporal (backend needs both):
   ```bash
   docker compose -f app/docker-compose.yml up -d postgres
   ```
2. Publish DSL platform to Maven Local:
   ```bash
   backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test
   ```
3. Start Spring Boot on the port the BFF expects:
   ```bash
   SERVER_PORT=8090 backend/dsl-platform/gradlew -p backend/dsl-starter :starter-launcher:bootRun -x test
   ```
4. Start Nuxt dev server:
   ```bash
   cd frontend && pnpm dev
   ```
5. Verify a proxied DSL endpoint:
   ```bash
   curl http://localhost:3000/api/v1/dsl/definitions
   ```

### Caveats

- **Java 25 is required.** All Gradle wrappers in the repo pin Gradle 9.4.1 (`./gradlew`, `backend/gradlew`, `backend/dsl-{platform,starter,plugins}/gradlew`, `app/{server,dsl}/gradlew`). The canonical per-build invocations are `backend/dsl-platform/gradlew` for platform/starter/plugin builds and the per-app wrappers under `app/` — the root and `backend/gradlew` wrappers are orchestration shells that Exec-invoke the sub-builds.
- **Port mismatch:** backend defaults to 8080, frontend BFF defaults to `http://localhost:8090`. Use `SERVER_PORT=8090` for backend, or override `BACKEND_BASE_URL` for the frontend.
- **No generic `/api/v1/dsl/*` catch-all.** BFF routes are explicit Nitro files under `frontend/admin-ui-plugin/server/api/v1/`. Add a matching proxy route when exposing a new backend DSL path.
- **Formatting gates:** `make lint` runs backend Spotless (`spotlessCheck` on dsl-platform/dsl-starter/dsl-plugins) + frontend Biome lint with a non-zero exit on failure; `make fmt` auto-fixes formatting on both tiers.

## Kanban CLI

When working with kanban tasks, use the project-local skill `cbs-nova-kanban`. It routes all board operations through
the `make kanban-*` CLI and enforces status/selection rules.

- Skill path: `~/.agents/skills/cbs-nova-kanban/`
- Invocation: load the skill before reading or editing `docs/kanban.md`.
- Prefer `make kanban-next`, `make kanban-start`, `make kanban-status`, `make kanban-add`, `make kanban-clean`, `make kanban-show`.
- Sandbox regression test: `make kanban-test`.
