# Developing cbs-nova

Local development guide for the cbs-nova Temporal DSL orchestration engine.

## Prerequisites

| Tool | Version | Why |
|------|---------|-----|
| **Java JDK** | 25 (or any 21+ supported by Spring Boot) | Spring Boot starter (`backend/starter`) |
| **pnpm** | 9.x | Nuxt frontend workspace |
| **Docker** | 24+ with Compose v2 | Runs Postgres / Keycloak / Bugsink / Temporal |
| **curl** | any modern version | Used by `make up` healthchecks |
| **GNU make** | any | The `make` targets |

On Debian / Ubuntu:

```bash
sudo apt-get install -y openjdk-25-jdk make curl
curl -fsSL https://get.pnpm.io/install.sh | sh
# Docker: https://docs.docker.com/engine/install/
```

## Quick start — `make dev`

The whole local stack (database, Keycloak, Bugsink, Temporal, Spring Boot, Nuxt)
is brought up with one command:

```bash
make dev
```

Under the hood:

1. `docker compose up -d` is run, then `make up` waits for Keycloak, Bugsink,
   and Temporal to answer HTTP before continuing.
2. `scripts/dev.sh` starts the Spring Boot backend (`./gradlew :starter:bootRun`)
   and the Nuxt admin UI (`pnpm dev`) in parallel, merging both logs into your
   terminal with `[backend]` / `[frontend]` prefixes.
3. SIGINT (Ctrl+C) and SIGTERM are trapped — child processes are killed
   cleanly so you don't leak a gradle daemon or a stuck node process.

If you'd rather start the pieces separately:

```bash
make up         # docker compose stack only, with healthcheck waits
make backend    # just the Spring Boot starter (assumes `make up` already ran)
make frontend   # just the Nuxt dev server
make logs       # tail docker compose logs (Ctrl+C to stop)
```

`make` (no arguments) prints the full target list.

## Services, ports, and default credentials

| Service | URL (from host) | Credentials | Notes |
|---------|-----------------|-------------|-------|
| Spring Boot backend | http://localhost:8090 | — | Started by `make backend`. |
| Nuxt admin UI | http://localhost:3000 | — | Started by `make frontend`. |
| Keycloak | http://localhost:8080 | `admin` / `admin` | Realm: `master`. Admin console at `/admin/master/console/`. |
| Keycloak Postgres | localhost:5433 | `keycloak` / `keycloak` (db: `keycloak`) | Host-side port mapping. |
| Bugsink | http://localhost:8000 | `admin` / `admin` | Sentry-compatible error tracker. |
| Temporal gRPC | localhost:7233 | — | Consumed by the backend via `TEMPORAL_ADDRESS`. |
| Temporal UI | http://localhost:8233 | — | Inspect workflows / activities. |

The backend reads `TEMPORAL_ADDRESS` from its environment. `docker-compose.yml`
already sets it to `temporal:7233` for the `spring-app` service. When you run
the backend on the host with `make backend`, the host-mapped port `7233`
makes `temporal:7233` resolve via Docker's DNS to the compose service — but
if you prefer, override with `TEMPORAL_ADDRESS=localhost:7233`.

## Logs and artifacts

`make dev` and `scripts/dev.sh` write child-process logs to `.dev-logs/` at
the repo root. The directory is created on first run and appended to on
subsequent runs; delete it to start fresh. (You may want to add `.dev-logs/`
to your local `.gitignore`.)

## Stopping the stack

| Command | Effect |
|---------|--------|
| `make down` | Stops docker compose services (volumes preserved). |
| `make clean` | **Destructive.** Stops services **and** removes all volumes — wipes Postgres data, Keycloak realm changes, Bugsink projects. |
| Ctrl+C in `make dev` | Stops the backend + frontend; docker services keep running. |

## Troubleshooting

- **`make up` hangs on Keycloak.** Keycloak can take 30–60 s on first boot.
  Inspect with `make logs` or `docker compose logs keycloak` for migration
  errors.
- **`make up` hangs on Temporal.** Temporal's auto-setup waits for its
  postgres to be ready. Inspect `docker compose logs temporal temporal-postgres`.
- **`make backend` fails with `JAVA_HOME not set`.** Install JDK 25 and
  export `JAVA_HOME`. The gradle wrapper (`./gradlew`) will pick it up.
- **`make frontend` fails with `command not found: pnpm`.** Install pnpm
  via `npm i -g pnpm` or `corepack enable`.
- **Port already in use.** Another process is bound to one of the host ports
  in the table above. Either stop it or edit `docker-compose.yml` to remap.
- **Stale stack after a config change.** Run `make down` (or `make clean`
  to also wipe volumes) and then `make up` again.
- **Backend cannot reach Temporal** (`UnknownHostException: temporal`).
  You're running the backend on the host but haven't started the compose
  stack. Run `make up` first.
- **Bugsink returns 502 on first request.** Wait ~10 s after `make up` for
  the gunicorn worker to fully start; the healthcheck only verifies the
  port is open, not that the app is serving requests.
- **`make dev` keeps running after Ctrl+C.** If you launched it from a
  terminal multiplexer (tmux/screen) without job control, the signal may
  not propagate to the child processes. Run `make dev` in a regular
  terminal or use `pkill -f 'gradlew :starter:bootRun'` and
  `pkill -f 'pnpm dev'` to clean up manually.

## Repo layout

```
backend/                       Spring Boot starter + DSL gradle plugin
  starter/                     The runnable Spring Boot app
frontend/                      Nuxt admin UI + reusable Vue components
  admin-ui-plugin/             The Nuxt module mounted by host apps
  components/                  Shared component library
docker-compose.yml             Postgres / Keycloak / Bugsink / Temporal
docs/                          Architecture, DSL reference, kanban
scripts/dev.sh                 Parallel backend + frontend launcher
Makefile                       `make dev`, `make up`, `make backend`, ...
.env.example                   Shared env vars (copy to .env)
```

For deeper design notes see `docs/architecture-backend.md`,
`docs/architecture-ui.md`, and `docs/architecture.md`.