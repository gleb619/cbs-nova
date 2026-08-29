# cbs-nova

## What it is

CBS-Nova: Business Orchestration Engine for core banking operations. Built on Java 25, Spring Boot, and Temporal workflows, with PostgreSQL persistence, Kotlin Script rules engine, and a Vue/Nuxt.js admin UI.

## Architecture at a glance

- **DSL authoring** — compact source files in [`backend/dsl-starter/dsl-examples/`](backend/dsl-starter/dsl-examples/) compiled by the Gradle DSL plugin into Temporal classes.
- **Spring Boot starter** (`backend/dsl-starter/starter/`) — runs the Temporal worker, exposes REST and introspection endpoints, handles Keycloak auth. The starter is opt-in OIDC-aware: the default (local/DX) configuration is fully anonymous; secured deployments flip `cbs.security.oidc.enabled=true` to require a JWT on `/api/dsl/**` and `/api/executions/**`. See [`app/compose/README.md`](app/compose/README.md#oidc--jwt-resource-server-guard-opt-in) for details.
- **`cbs.runs.retention`** (Duration, default `0` / disabled) — opt-in scheduled purge of finished `dsl_runs` rows. Set a positive duration (e.g. `P7D`) to delete terminal runs (`COMPLETED`, `FAILED`, `STALE`, `CANCELLED`) whose `finished_at` is older than `now - retention`. `RUNNING` rows are never deleted. Tune `cbs.runs.purge-interval` and `cbs.runs.purge-batch-size` (default 500) as needed.
- **Temporal cluster** — workflows and activities run on a Temporal cluster fronted by Postgres; the UI is exposed for inspection.
- **Keycloak** — OIDC issuer for the admin UI and integration tests.
- **Admin UI** ([`frontend/admin-ui-plugin/`](frontend/admin-ui-plugin/)) — a Nuxt module that mounts the UI into any host Nuxt app, with a Nitro BFF in front of the backend.

See [`docs/architecture-backend.md`](docs/architecture-backend.md) and [`docs/architecture-ui.md`](docs/architecture-ui.md) for the full designs.

## Prerequisites

- **JDK 25** (any 21+ supported by Spring Boot works) for the backend.
- **pnpm 9.x** for the Nuxt frontend workspace.
- **Docker 24+ with Compose v2** for the Postgres / Keycloak / Bugsink / Temporal stack.

See [`DEVELOPING.md`](DEVELOPING.md) for per-platform install instructions and the `make` targets.

## Quickstart

`docker compose up` starts the full containerized stack today: Postgres / Keycloak / Bugsink / Temporal / 
`spring-app` (built from [`app/Dockerfile`](app/Dockerfile)), with the backend reachable on host port 8090.

For local development with hot reload, [`Makefile`](Makefile) target `dev` runs [`scripts/dev.sh`](scripts/dev.sh), which starts the backend and frontend in parallel on the host. It assumes the Compose infrastructure is already up. Because the containerized `spring-app` also binds host port 8090, start only the infrastructure services first to avoid the collision, then run `make dev`:

```bash
docker compose up -d postgres-keycloak keycloak bugsink-db bugsink temporal-postgres temporal temporal-ui
make dev
```

Then open the Spring Boot app on http://localhost:8090 and the Nuxt UI on http://localhost:3000.

**Manual fallback** if you'd rather start the pieces by hand:

```bash
# from backend/
./gradlew :starter:bootRun
# from frontend/
pnpm dev
```

## Key ports

| Service | Host port | Credentials |
|---------|-----------|-------------|
| Spring Boot backend | 8090 | — |
| Nuxt dev server (local dev only) | 3000 | — |
| Keycloak | 8080 | `admin` / `admin` |
| Bugsink | 8000 | `admin` / `admin` |
| Temporal gRPC | 7233 | — |
| Temporal UI | 8233 | — |
| Keycloak Postgres | 5433 | `keycloak` / `keycloak` (db: `keycloak`) |

Bugsink Postgres and Temporal Postgres run inside the Compose network only and are not exposed to the host.

## DSL in 60 seconds

- Authoring guide: [`docs/dsl/authoring.md`](docs/dsl/authoring.md)
- Working examples: [`backend/dsl-starter/dsl-examples/`](backend/dsl-starter/dsl-examples/)

## Testing

```bash
# backend (from backend/)
./gradlew test

# frontend (from frontend/)
pnpm --filter @cbs/admin-ui-plugin test
```

## Project layout

```
backend/        Spring Boot starter, DSL compiler, Gradle plugin, examples
frontend/       Nuxt admin UI plugin + shared Vue components (pnpm workspace)
docs/           Architecture, DSL authoring guide, kanban
```

## Tracing

The Spring Boot starter emits OpenTelemetry traces for every DSL run when `OTEL_EXPORTER_OTLP_ENDPOINT` is set (for example, `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces`). The default compose stack already wires this to the local collector and Jaeger; start the stack, run a DSL process, and open http://localhost:16686 to browse traces such as `dsl.run.<processName>`. When the OTLP endpoint is unset, tracing is a no-op and produces no exports or error logs.

For service ports, troubleshooting, and the full make-target list, see [`DEVELOPING.md`](DEVELOPING.md).
