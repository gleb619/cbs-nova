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
| `rabbitmq.yml` | RabbitMQ broker (AMQP `5672`, management UI `15672`) for the `amqpPublish` @Helper demo |

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
| RabbitMQ AMQP | localhost:5672 |
| RabbitMQ management UI | http://localhost:15672 |

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

## Grafana

Grafana boots fully provisioned — no manual datasource or dashboard setup:

- Datasources (provisioned from `grafana/provisioning/datasources/datasources.yml`):
  Prometheus (`http://prometheus:9090`, default), Loki (`http://loki:3100`), Jaeger (`http://jaeger:16686`).
- Dashboards (provisioned from `grafana/provisioning/dashboards/dashboards.yml`):
  a file provider loads every JSON in `grafana/dashboards/`. Ships with the
  **CBS Nova — DSL Overview** dashboard (`dsl-overview.json`) at
  http://localhost:3000/d/cbs-nova-dsl-overview.

To add another dashboard, drop a hand-written or exported dashboard JSON into
`grafana/dashboards/` and recreate the Grafana container
(`docker compose up -d --force-recreate grafana`). Panels should reference the
Prometheus datasource by uid (`cbs-nova-prometheus`), never a numeric id.

## OIDC / JWT resource-server guard (opt-in)

The Spring Boot starter (`backend/dsl-starter`) ships with an opt-in OIDC
resource-server guard. It is **OFF by default** — every DSL endpoint stays
anonymous, matching the pre-T275 behaviour.

To enable against the local Keycloak, override the spring-app environment:

```yaml
cbs.security.oidc.enabled: true
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://keycloak:8080/realms/cbs-nova
```

When enabled, `/api/dsl/**` and `/api/executions/**` require a Bearer JWT
issued by the configured Keycloak realm. Actuator health, springdoc/Swagger
and any path listed in `cbs.security.oidc.permit-all-paths` stay anonymous.
401 (not 500) with `WWW-Authenticate: Bearer ...` is returned for missing or
invalid tokens.

The compose stack now bootstraps a ready-to-use realm automatically on first
start. `app/compose/keycloak/cbs-nova-realm.json` is imported by Keycloak via
`--import-realm`; because the realm is persisted in Postgres, later restarts do
not re-import it. The imported realm contains:

- Realm `cbs-nova`.
- Confidential client `cbs-nova-bff` with secret `change_me_in_production`.
- Dev user `devuser` / `devpassword`.
- Realm role `cbs-nova-user` (not enforced by the backend yet; exists for
  future role-based checks).

### Token acquisition examples

Client-credentials grant (service-account token):

```bash
curl -s -X POST http://localhost:8080/realms/cbs-nova/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=cbs-nova-bff' \
  -d 'client_secret=change_me_in_production'
```

Password grant (dev user token):

```bash
curl -s -X POST http://localhost:8080/realms/cbs-nova/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=cbs-nova-bff' \
  -d 'client_secret=change_me_in_production' \
  -d 'username=devuser' \
  -d 'password=devpassword'
```

Either response contains `access_token`. Use it to call a guarded endpoint:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/cbs-nova/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=cbs-nova-bff' \
  -d 'client_secret=change_me_in_production' | jq -r '.access_token')

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8090/api/dsl/definitions
```

Anonymous health checks remain reachable:

```bash
curl -s http://localhost:8090/actuator/health
```

See
`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/SecurityConfiguration.java`
for the exact path patterns.

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
| `TEMPORAL_UI_BASE_URL` | empty (feature disabled) | Nuxt admin UI — when set (e.g. `http://localhost:8233`), the executions detail view renders a "View in Temporal" deep-link on the Workflow ID row. Set `TEMPORAL_NAMESPACE` to override the default `'default'` namespace. |
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
| `RABBITMQ_USER` | `guest` | RabbitMQ default user (matches `spring.rabbitmq.username`) |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ default password (matches `spring.rabbitmq.password`) |

## RabbitMQ (opt-in broker for `amqpPublish`)

The `amqpPublish` @Helper (`backend/dsl-starter/.../helper/AmqpPublishHelper.java`) publishes
through the host Spring app's `AmqpTemplate` bean. Spring Boot auto-configures `AmqpTemplate`
only when `spring-boot-starter-amqp` is on the runtime classpath AND a broker is reachable at
`spring.rabbitmq.host:spring.rabbitmq.port` (defaults `localhost:5672`, user/pass `guest/guest`).

The starter-launcher (`backend/dsl-starter/starter-launcher`) ships `spring-boot-starter-amqp`
on the implementation classpath, so the bean is created — but inside the Compose network the
`localhost` default won't resolve the broker. To point the Spring app at the local RabbitMQ
service, add the following to `app/compose/app.yml` under `spring-app.environment`:

```yaml
SPRING_RABBITMQ_HOST: rabbitmq
SPRING_RABBITMQ_PORT: "5672"
SPRING_RABBITMQ_USERNAME: "${RABBITMQ_USER:-guest}"
SPRING_RABBITMQ_PASSWORD: "${RABBITMQ_PASSWORD:-guest}"
```

The `exchange` and `routingKey` arguments to `amqpPublish` are passed through unchanged from the
helper input — no defaults. Declare an exchange on the broker (e.g. `cbs.nova.events.exchange`
via the management UI at `http://localhost:15672`) before invoking `AmqpPublishDsl`, or wire a
queue + binding first.

If the broker is down or the bean is missing, `amqpPublish` returns
`Result.failure("amqpPublish: AmqpTemplate bean is not available; RabbitMQ is not configured")`
— the rest of the DSL stays unaffected.
