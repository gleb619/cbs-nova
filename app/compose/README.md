# CBS Nova local Docker Compose domains

This directory contains the split Compose domain files used by `../docker-compose.yml`.

## Domains

| File | Purpose |
|------|---------|
| `postgres.yml` | Shared Postgres instance provisioning the per-domain databases |
| `auth.yml` | Keycloak identity provider (uses shared Postgres) |
| `error-tracking.yml` | Bugsink error tracker (uses shared Postgres) |
| `orchestration.yml` | Temporal server and UI (uses shared Postgres) |
| `app.yml` | CBS Nova Spring Boot application |
| `gitea.yml` | Gitea git hosting (uses shared Postgres) |
| `observability.yml` | Grafana, Loki, Jaeger, Prometheus, OpenTelemetry Collector |

## Run everything

```bash
cd app
docker compose up -d
```

## Run a subset

`include` always loads all files referenced by the root manifest. To run a subset, use service names:

```bash
cd app
docker compose up -d temporal temporal-ui postgres
```

## Shared Postgres

A single Postgres instance backs Keycloak, Gitea, Bugsink and Temporal. On
first boot `compose/postgres-initdb.d/01-create-dbs.sh` creates the four
databases and roles. The data volume is named `postgres-data`; deleting it
re-runs the init script on next start.

DB credentials default to the historical per-service values. Override any of
them via the matching `*_DB_PASSWORD` env var on the `postgres` service (see
the table below).

## Ports

| Service | URL |
|---------|-----|
| CBS Nova app | http://localhost:8090 |
| Keycloak | http://localhost:8080 |
| Bugsink | http://localhost:8000 |
| Temporal UI | http://localhost:8233 |
| Grafana | http://localhost:3000 |
| Jaeger UI | http://localhost:16686 |
| Prometheus | http://localhost:9090 |
| Gitea | http://localhost:3001 |
| Loki | http://localhost:3100 |
| Postgres | localhost:5432 |

## Observability wiring

The app container is pre-configured with OTLP environment variables:

- `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces`
- `MANAGEMENT_OTLP_LOGS_ENDPOINT=http://otel-collector:4318/v1/logs`

To see traces in Jaeger and logs in Grafana/Loki, ensure the backend has:

- `io.micrometer:micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springframework.boot:spring-boot-starter-actuator`

and expose `/actuator/prometheus` via `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`.

## Environment variables

| Variable | Default | Used by |
|----------|---------|---------|
| `POSTGRES_PASSWORD` | `change_me_secure_password` | Shared Postgres superuser |
| `KEYCLOAK_DB_PASSWORD` | `keycloak` | Shared Postgres init + Keycloak |
| `BUGSINK_DB_PASSWORD` | `change_me_secure_password` | Shared Postgres init + Bugsink app |
| `TEMPORAL_DB_PASSWORD` | `temporal` | Shared Postgres init + Temporal |
| `BUGSINK_SECRET_KEY` | `your_super_secret_key_at_least_50_chars_long` | Bugsink app |
| `BUGSINK_SUPERUSER` | `admin:admin` | Bugsink app |
| `BUGSINK_DSN` | empty | Spring app Sentry integration |
| `KEYCLOAK_URL` | empty | Spring app |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring app |
| `GRAFANA_ADMIN_USER` | `admin` | Grafana |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Grafana |
| `OTEL_SERVICE_NAME` | `cbs-nova` | OpenTelemetry resource attribute |
| `OTEL_RESOURCE_ATTRIBUTES` | `deployment.environment=local` | OpenTelemetry resource attributes |
| `GITEA_DB_PASSWORD` | `gitea` | Shared Postgres init + Gitea app |
| `GITEA_DOMAIN` | `localhost` | Gitea domain |
| `GITEA_ROOT_URL` | `http://localhost:3001/` | Gitea root URL |
| `GITEA_SSH_DOMAIN` | `localhost` | Gitea SSH domain |
| `GITEA_DISABLE_REGISTRATION` | `false` | Gitea user registration |
| `GITEA_REQUIRE_SIGNIN_VIEW` | `false` | Gitea require sign-in to view |
| `TRACING_SAMPLING` | `1.0` | Spring tracing sampling probability |
