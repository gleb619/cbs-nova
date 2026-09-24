# Operator Incident Runbook

First-response playbook for cbs-nova production incidents. Each entry is **Symptom → Triage →
Hypotheses → Mitigation → Permanent fix → Verification**. Follow the steps top to bottom; the
goal is operator-mitigates-in-under-5-minutes.

**Scope:** operational level only — no code-level debugging, no internal class names beyond what
an operator needs to grep a log. For architecture, see
[`architecture-backend.md`](architecture-backend.md) (security & ops layer) and
[`dsl/configuration.md`](dsl/configuration.md) (every property named here).

## Multi-instance deployment — DSL file flush contract

Multiple app replicas may share one Postgres and one workbench workspace directory. DSL file
flush is safe under that setup: each replica stages edits in its own in-JVM buffer, and every
file is published atomically (temp file + atomic rename in the target directory), so concurrent
flushes are last-write-wins per file — readers never see truncated or interleaved content and
no cross-process lock is needed. Writes to the *same* DSL file from two replicas within one
flush interval can still overwrite each other (last flush wins); treat a shared workspace as
single-writer-per-file by routing edits through one replica or accepting overwrite semantics.

## Reference — where things live

| Thing | Local URL | Notes |
|---|---|---|
| CBS Nova app | `http://localhost:8090` | Spring Boot; `SERVER_PORT` in prod |
| Actuator health | `http://localhost:8090/actuator/health` | always anonymous |
| Admin UI (Nuxt host + BFF) | `http://localhost:3000` | browser → BFF → app |
| Keycloak | `http://localhost:8080` | realm `cbs-nova` |
| Temporal UI | `http://localhost:8233` | server gRPC `7233` |
| Bugsink (error tracking) | `http://localhost:8000` | Spring app ships errors via `BUGSINK_DSN` |
| Grafana | `http://localhost:3000` (compose) | Micrometer metrics |
| Postgres | `localhost:5432` | shared by app, Keycloak, Temporal, Bugsink |

Key metrics (Micrometer, scraped from `/actuator/prometheus` when enabled):
`dsl.run.duration`, `dsl.run.count`, `dsl.run.cancel` (tagged `processName`, `status`);
`dsl.preview.calls`, `dsl.preview.external.calls`, `dsl.preview.duration`;
`dsl.runs.purged`, `dsl.run.transactions.purged`;
`dsl.run.reconciliation.inspected`, `dsl.run.reconciliation.resolved`.

Common triage commands:

```bash
curl -s http://localhost:8090/actuator/health | jq
docker compose -f app/docker-compose.yml ps
docker compose -f app/docker-compose.yml logs --tail=200 app
make logs                       # tail all compose services
```
---

## Health endpoints

Use the three actuator health URLs to decide whether a pod is alive, ready to take traffic, or
unhealthy overall. Each endpoint answers a different fault domain.

| Endpoint | What it reports | Included checks | When it is DOWN | Operator action |
|---|---|---|---|---|
| `/actuator/health` | Overall composite | All active indicators (`livenessState`, `readinessState`, `db`, `dsl`, `dslReadiness`, ...) | Any included indicator reports DOWN | Identify the failing component and follow the matching incident below. |
| `/actuator/health/liveness` | In-process liveness only | `livenessState` | Only when the Spring context/JVM itself cannot respond (crash, OOM, deadlock). | Investigate the process; **do not restart the pod just because Temporal or DB are down**. |
| `/actuator/health/readiness` | Ready to serve traffic | `readinessState`, `db` (launcher/production profile), `dslReadiness` | Temporal is unreachable **and** `cbs.health.temporal.fail-status=down`; or the database is down. | Pull the pod from the load balancer; fix the dependency. Set `cbs.health.temporal.fail-status=none` to keep serving while Temporal is down. |

**Temporal reachability is a readiness-only failure.** Liveness intentionally makes no external
calls, so a Temporal outage does **not** cause Kubernetes (or any orchestrator) to restart the
pod. If readiness is DOWN and the orchestrator stops sending traffic, the pod stays up and
re-enters rotation automatically once the dependency recovers.

**Liveness stays UP during external dependency outages.** If liveness is DOWN, suspect an
in-process problem (memory pressure, thread starvation, startup failure) rather than a
Temporal/Postgres outage.

**Readiness DOWN for Temporal is opt-in.** The default `cbs.health.temporal.fail-status=none`
keeps readiness UP even when Temporal is unreachable. Operators who want load balancers to stop
traffic during a Temporal outage must explicitly set `cbs.health.temporal.fail-status=down`.

**Symptom → Triage → Mitigation:**
1. `curl -s http://localhost:8090/actuator/health/liveness \| jq .status` — should be `UP`.
2. `curl -s http://localhost:8090/actuator/health/readiness \| jq .status` — `DOWN` means stop
traffic; check whether `dslReadiness` or `db` is the failing component.
3. `curl -s http://localhost:8090/actuator/health \| jq .components` — see the full composite to
tell an external-dependency problem from an internal one.

**Verification.** After the dependency recovers, `/actuator/health/readiness` returns `UP` and
`dslReadiness.details.temporal.reachable` becomes `true`; the pod re-enters rotation without a
restart.

---

## 1. Temporal disconnect  — P0

**Symptom.** Runs stay `RUNNING` forever; new `POST /api/dsl/run/*` calls hang or 5xx; Temporal
UI unreachable; app logs show gRPC `UNAVAILABLE` / `DEADLINE_EXCEEDED` to `:7233`.

**Triage.**
```bash
curl -s http://localhost:8090/actuator/health | jq '.components.temporal // .components'
docker compose -f app/docker-compose.yml ps temporal
docker compose -f app/docker-compose.yml logs --tail=100 temporal
grep -i "temporal" <app-log> | tail -50
```

**Hypotheses (ranked).**
1. Temporal server container down / crash-looping (OOM, disk full on shared Postgres).
2. Shared Postgres unavailable — Temporal, Keycloak and the app all fail together.
3. Network partition between app and `temporal:7233`.
4. Temporal namespace `default` missing (fresh volume, failed init).

**Mitigation.**
- Restart Temporal: `docker compose -f app/docker-compose.yml restart temporal`.
- If Postgres is the cause, fix that first (incident #3 shares the root).
- Preview/Explain mode does **not** need Temporal — direct users to preview for validation while
  Run is degraded.
- Set `cbs.health.temporal.fail-status=DOWN` only if you *want* readiness probes to pull the app
  out of rotation while Temporal is down (default `NONE` keeps it serving).

**Permanent fix.** Temporal HA / managed Temporal; separate datastore from Keycloak. No kanban
task yet — raise one if this recurs.

**Verification.** `/actuator/health` Temporal component `reachable=true`; Temporal UI loads; a
fresh preview→run of a trivial process completes; run the reconciliation job (incident #4) to
sweep rows stranded during the outage.

---

## 2. OIDC / Keycloak outage  — P1

**Symptom.** Login loop in the admin UI; sudden spike of `401`/`403` from `/api/dsl/**` and
`/api/executions/**`; app logs show JWT decode failures / `issuer-uri` unreachable.

**Triage.**
```bash
curl -s http://localhost:8080/realms/cbs-nova/.well-known/openid-configuration | jq .issuer
docker compose -f app/docker-compose.yml logs --tail=100 keycloak
# is OIDC even on?
grep -i "cbs.security.oidc" <app-config>
```

**Hypotheses.**
1. Keycloak container down or its Postgres schema unavailable.
2. `spring.security.oauth2.resourceserver.jwt.issuer-uri` wrong / unreachable from the app
   network (container hostname vs localhost).
3. Clock skew between app and Keycloak → every token "expired".
4. Realm/key rotation — cached JWKS stale.

**Mitigation.**
- Restart Keycloak; confirm realm `cbs-nova` imported.
- **Emergency bypass:** set `cbs.security.oidc.enabled=false` and restart the app — this drops to
  the permissive filter chain. Only acceptable behind a trusted network perimeter; re-enable ASAP.
- If only the UI is affected and the API is fine, the BFF's one-shot refresh-on-401 may be
  exhausting `cbs_rt` — clear the user's session cookie and re-login.

**Permanent fix.** Keycloak HA; NTP on all hosts. Related surface: T275 (BFF auth), T287
(rate-limiting interaction), T288.

**Verification.** Token fetch succeeds:
```bash
curl -s -X POST http://localhost:8080/realms/cbs-nova/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=<id> -d client_secret=<secret> | jq .access_token
```
Authenticated `GET /api/dsl/definitions` returns `200`; UI login completes.

---

## 3. Retention purger stuck or too aggressive  — P1

**Symptom.**
- *Stuck / not running:* `dsl_runs` row count grows unbounded; disk pressure on Postgres;
  `dsl.runs.purged` counter flat.
- *Too aggressive:* runs disappearing from the Executions list sooner than expected; long
  `DELETE` statements holding locks; write latency spikes on the hour.

**Triage.**
```sql
SELECT count(*), min(started_at), max(finished_at) FROM dsl_runs;
SELECT count(*) FROM dsl_runs WHERE finished_at < now() - interval '30 days';
```
```bash
grep -i "retention\|purge" <app-log> | tail -30
# effective config:
grep -E "cbs.runs.(retention|purge-interval|purge-batch-size)" <app-config>
```

**Hypotheses.**
1. `cbs.runs.retention` is `0`/negative → **purge job never registered** (this is the default;
   growth is expected unless someone set it).
2. `cbs.runs.retention` set too short → legitimate history being deleted.
3. `purge-batch-size` too large → each pass takes a long lock; `purge-interval` too short →
   passes overlap.
4. Purge failing mid-batch on a constraint / long-running transaction and never advancing.

**Mitigation.**
- *Growth:* set `cbs.runs.retention` to a sane duration (e.g. `P90D`) and restart; the job runs
  every `cbs.runs.purge-interval` (default `PT1H`).
- *Too aggressive:* raise `cbs.runs.retention`; **stop the bleeding immediately** by setting it to
  `0` (disables the job) until you pick the right value. Purged rows are gone — restore from
  backup if history matters.
- *Lock storms:* lower `cbs.runs.purge-batch-size` (e.g. `100`), raise `cbs.runs.purge-interval`.

**Permanent fix.** T276 (retention), T315 (`dsl_runs` purge index). Partition `dsl_runs` by month
if volume warrants.

**Verification.** `dsl.runs.purged` / `dsl.run.transactions.purged` counters advance; row count
stabilises; no `DELETE` in `pg_stat_activity` older than a few seconds.

---

## 4. Stuck-RUNNING reconciliation lag  — P2

**Symptom.** Executions list shows runs `RUNNING` for hours that Temporal UI reports as
`COMPLETED`/`FAILED`; dashboard stats (`/api/executions/stats`) inflated with phantom running
counts.

**Triage.**
```sql
SELECT run_id, process_name, started_at FROM dsl_runs
WHERE status = 'RUNNING' AND started_at < now() - interval '1 hour'
ORDER BY started_at LIMIT 50;
```
```bash
grep -i "reconciliation" <app-log> | tail -20
grep -E "cbs.runs.reconciliation" <app-config>
```

**Hypotheses.**
1. Reconciliation job disabled (`cbs.runs.reconciliation.enabled` default `false`) — nothing
   sweeps stranded rows.
2. Job enabled but `grace-period` (default `15m`) longer than the drift you're seeing — working
   as designed, wait a cycle (`scan-interval`, default `5m`).
3. `batch-size` (default `200`) too small for a large backlog after a Temporal outage — it
   catches up one batch per scan.
4. Temporal describe API failing → job skips and retries, never marks `STALE`.

**Mitigation.**
- Enable the job: `cbs.runs.reconciliation.enabled=true`, restart. It maps each stuck row to the
  real Temporal terminal status; rows genuinely gone from Temporal become `STALE`.
- After a big outage, temporarily raise `batch-size` and lower `scan-interval` to drain faster,
  then revert.

**Permanent fix.** T316 (stuck-run reconciliation).

**Verification.** `dsl.run.reconciliation.inspected` / `...resolved` counters advance; the query
above returns few/no rows; stats endpoint running-count matches Temporal.

---

## 5. BFF proxy 5xx storm  — P1

**Symptom.** Admin UI pages show errors; browser network tab full of `502`/`504` from
`/api/v1/**`; Nitro (host Nuxt) logs show upstream failures to the Spring Boot base URL.

**Triage.**
```bash
curl -s http://localhost:8090/actuator/health | jq .status      # backend up?
curl -s http://localhost:3000/api/v1/dsl/definitions | head      # BFF path directly
# host Nuxt / Nitro logs (wherever the host app logs):
grep -iE "proxyToBackend|ECONNREFUSED|ETIMEDOUT|upstream" <nuxt-log> | tail -40
```

**Hypotheses.**
1. Backend down or unhealthy — BFF is just the messenger (go to incident #1/#3).
2. `BACKEND_BASE_URL` / `backendBaseUrl` misconfigured (port mismatch: backend `8090`, BFF
   default `http://localhost:8090`).
3. BFF request timeout too low for a slow endpoint (large `definitions`, cold preview).
4. A specific route only: missing Nitro proxy file → `404` (not `5xx`) — see incident #6 pattern.
5. Rate limiter (`cbs.security.ratelimit.enabled=true`) returning `429` on mutating routes,
   surfaced by the UI as a failure — check for `Retry-After` headers.

**Mitigation.**
- If backend is healthy, restart the host Nuxt process to reset Nitro connection pools.
- Fix `backendBaseUrl` / `BACKEND_BASE_URL`; the browser must never be pointed at Spring Boot
  directly (CORS + token exposure).
- Raise the BFF timeout for the offending route.
- If `429`s: raise `cbs.security.ratelimit.capacity` (default `20`) /
  `cbs.security.ratelimit.refill-per-second` (default `5.0`), or set
  `cbs.security.ratelimit.enabled=false` to confirm causation.

**Permanent fix.** T335 (BFF↔backend contract shape tests) catches shape drift; route coverage
tests catch missing proxies.

**Verification.** `curl` of each failing BFF path returns `200` with expected JSON shape; UI
pages load; no upstream errors in Nitro logs for 5 minutes.

---

## 6. Helper catalog empty / helper missing  — P2

**Symptom.** `GET /api/dsl/helpers` returns an empty (or short) `names` list; DSL Workbench
helper picker empty; a newly added helper doesn't appear after a deploy; runs fail with
"cannot resolve helper `<name>`".

**Triage.**
```bash
curl -s http://localhost:8090/api/dsl/helpers | jq '.names'
grep -iE "helper|GeneratedHelper|ServiceLoader" <app-log> | tail -30
```

**Hypotheses.**
1. Stale build — the `misc-codegen` annotation processor didn't run, so
   `GeneratedHelperInstanceResolver` / `GeneratedHelperResolver` don't include the helper. A
   clean rebuild is required (see [ADR 0002](adr/0002-helper-spi.md) — there is **no reflection
   fallback**).
2. The helper jar / DSL module isn't on the app classpath (SPI file not found).
3. `@SpringHelper` helper: its Spring bean failed to construct (missing dependency bean) — check
   for a startup `BeanCreationException`.
4. Wrong deploy artifact.

**Mitigation.**
- Rebuild and redeploy from a clean tree:
  `backend/dsl-platform/gradlew -p backend/dsl-starter :starter:clean :starter:build`.
- Confirm the DSL module jar is present in the deployed classpath.
- For a `@SpringHelper` failure, fix the missing collaborator bean.

**Permanent fix.** T311 (helper catalog). CI check that `/api/dsl/helpers` count matches the
expected helper set.

**Verification.** `GET /api/dsl/helpers` lists the expected helper; a preview run invoking it
succeeds.

---

## 7. Frontend auto-refresh storm  — P2

**Symptom.** Browser tab memory climbs; Executions/Dashboard page fires the same request every
few hundred ms; backend sees a burst of identical `GET /api/executions*` from one client.

**Triage.**
- Browser devtools → Network → confirm the repeating request and its interval.
- Backend access log: many identical GETs from one IP/session in a short window.
- Check whether rate limiting is on — GETs are exempt, so a storm still reaches the backend.

**Hypotheses.**
1. A polling composable re-arming its interval on every render (dependency loop) — regression in
   a list page.
2. Multiple mounted copies of the same page/component each polling.
3. An error-retry loop: request fails fast, handler retries immediately with no backoff.

**Mitigation.**
- Have the user close the offending tab — stops the client-side loop immediately.
- If widespread, roll back the most recent admin-ui-plugin deploy.
- Backend is read-only-affected; no data risk. Add a temporary rate cap at the ingress if needed.

**Permanent fix.** T269 (auto-refresh). Polling composables must use a fixed interval cleared on
unmount and back off on error.

**Verification.** With the page open, one request per configured interval (not per frame);
memory flat over a few minutes.

---

## 8. Preview sandbox-escape request  — P1 (advisory)

**Symptom.** Someone asks for arbitrary code / untrusted DSL to be run through Preview mode, or
reports that Preview executed something with a real side effect (filesystem, network, process).

**Triage.**
- Preview executes DSL definitions **directly in the app JVM** with no isolation. `httpCall` and
  other external-effect helpers are *recorded, not sent* in preview — but arbitrary Java in a
  helper or function body runs for real.
- Confirm the source of the DSL: only trusted, reviewed definitions from the DSL module should
  ever be loaded.

**Hypotheses.**
1. Untrusted DSL was loaded via SPI / reload from an unreviewed source.
2. A helper with a genuine side effect was invoked in a preview and the effect is not
   preview-guarded.

**Mitigation.**
- Do **not** load untrusted DSL. Restrict `POST /api/dsl/reload` and draft publish endpoints
  (API-key filter `dsl.auth.api-key`, or OIDC).
- If untrusted DSL was loaded, treat as a host compromise: rotate secrets reachable from the app,
  audit `dsl_run_transactions` and external-call records for the window.

**Permanent fix.** T165 (preview execution sandboxing) — **currently blocked**: JDK 25 removed
`SecurityManager` enforcement (JEP 486), so an alternative isolation approach needs a human
decision. Until then, **preview is only as safe as the DSL you load**.

**Verification.** `POST /api/dsl/reload` and draft-publish require auth; only reviewed DSL module
artifacts are deployed.

---

## Correlating a request across logs

Every request carries two ids. Both are optional — the caller may send either, neither, or both:

- **`X-Request-Id`** — per-hop request id. If absent, the app generates a UUID and echoes it
  back on the response so the caller learns it. The BFF also generates one per backend call.
- **`X-Correlation-Id`** — caller-owned business-transaction id. The server **never**
  generates one; absence means absent. Persisted on `dsl_runs.correlation_id` for run-path
  lookups (see incident #4 for stuck runs).

Where they show up:

- **Backend log lines** carry both MDC keys on every line:
  `INFO [rid=3f0a…, cid=order-4711] …`. Uncorrelated requests render the segment with empty
  values (`[rid=3f0a…, cid=]`) — no noise, no `null`.
- **BFF logs** (Nuxt/Nitro console) print a structured object next to each
  `[BFF >]` / `[BFF <]` / `[BFF !]` line containing `requestId` and, when the browser sent
  one, `correlationId`.
- **`dsl_runs.correlation_id`** — query it directly to map a cid to its runs, then grep the
  app log by the run ids.
- **Distributed traces** — Jaeger UI at `http://localhost:16686`, service `spring-app`, one
  trace per request spanning BFF → backend → DSL dispatch → Temporal activity (compose
  stack only; no-op outside compose — [Tracing](architecture-backend.md#tracing-in-compose-default-on)).

Trace one correlation id end-to-end:

```bash
# 1. Backend: every log line for this business transaction
docker compose -f app/docker-compose.yml logs app | grep 'cid=order-4711'

# 2. BFF: structured log objects mentioning the same cid (console.log of an object —
#    Node renders it, jq not applicable; grep the plain text)
docker compose -f app/docker-compose.yml logs frontend | grep 'order-4711'

# 3. Run path: map the cid to run rows, then to the request ids in the app log
docker exec -i $(docker ps -qf name=postgres) psql -U nova -d nova \
  -c "SELECT run_id, process_name, status, started_at FROM dsl_runs WHERE correlation_id='order-4711';"

# 4. Distributed trace: open Jaeger UI and find the trace for this request
#    correlation_id is not yet a searchable span attribute, so search by
#    operation + time window:
#    - Open http://localhost:16686 → service "spring-app"
#    - Select the operation (e.g. "dsl.run.<processName>") and set the time
#      window to cover the request.
#    - If the client captured the traceparent response header, paste the
#      trace-id directly into Jaeger's search bar.
#    The span chain shows BFF → backend → DSL dispatch → Temporal activity.
#    A gap between two adjacent spans usually means network latency (BFF→backend
#    or backend→Temporal) rather than processing time — check the span
#    timestamps to confirm.
#    Follow-up: adding correlation_id as a span attribute would allow direct
#    Jaeger tag search (see docs/architecture-backend.md §Tracing).
#    Is the trace pipeline even up? Run:
make trace-smoke
```

Feed a request a correlation id from the admin UI's API or any client:

```bash
curl -H 'X-Correlation-Id: order-4711' -H 'Content-Type: application/json' \
  -d '{"body":{}}' http://localhost:8090/api/dsl/run/<name>
```

An `X-Correlation-Id` that violates the charset/length rules (`[A-Za-z0-9_.:/-]`, ≤200 chars)
is rejected with `400 INVALID_CORRELATION_ID` before any handler runs.

Trace pipeline not producing spans → see the troubleshooting checklist in
[docs/architecture-backend.md §Tracing](architecture-backend.md#tracing-in-compose-default-on).

---

## Monitoring

Prometheus scrapes the Spring Boot actuator at `http://spring-app:8090/actuator/prometheus` (job `cbs-nova`). The exposed DSL metric families include `dsl_run_*`, `dsl_preview_*`, and JVM/process series. The first alert rules live in `app/compose/alerts.yml`. SLOs (run success rate, run p95, preview p95) with SLI queries, error budgets, and the burn-rate policy are defined in [docs/slo.md](slo.md) (T490).

### Alert rules

| Alert | Meaning | Operator action |
|---|---|---|
| `CbsNovaRunErrorRate` | More than 10% of DSL runs finished with a non-`SUCCESS` status over the last 5 minutes, sustained for 10 minutes. | Check Temporal connectivity and recent deployments; grep app logs for the failing `processName` and `status`. |
| `CbsNovaPreviewLatencyP95` | The 95th percentile of DSL preview durations exceeded 2 seconds for 10 minutes. | Tune the threshold to your observed baseline; investigate if preview compute or helper mocks are slow. A true preview timeout-rate alert needs an outcome-tagged preview counter (follow-up task). |
| `CbsNovaAppDown` | Prometheus cannot scrape the `cbs-nova` job for 2 minutes. | Verify the app container is running, `SERVER_PORT`, and the `management.endpoints.web.exposure.include` list contains `prometheus`. Temporal-specific reachability alerting rides on the future `dsl_temporal_reachable` gauge (T390). |
| `CbsNovaSloRunSuccessFastBurn` / `CbsNovaSloRunSuccessSlowBurn` | The run-success SLO (see [docs/slo.md](slo.md)) is burning its 28d error budget at >14.4x (page) or >6x (ticket). | Page (fast burn) or open a reliability ticket (slow burn); check Temporal connectivity and recent deployments. |

### First-boot verification checklist

Before trusting the alert expressions, boot the app and confirm the real series names:

```bash
curl -s http://localhost:8090/actuator/prometheus | grep -E "dsl_run|dsl_preview" | head
```

If the counter or bucket names differ from the conventional names used in `alerts.yml`, update the expressions and this runbook to match the live output. This check is intentionally deferred to a human or CI boot because it requires Postgres + Temporal.

---

## Reading errors in Bugsink

Bugsink is a self-hosted Sentry-compatible error tracker. The Spring Boot app ships unhandled exceptions to Bugsink via the Sentry SDK when `SENTRY_DSN` is set.

**URL:** `http://localhost:8000` (compose port mapping in `app/compose/error-tracking.yml`).

**Default superuser:** `admin@example.com` / `admin` — created automatically by the `CREATE_SUPERUSER` env var on first boot. This is a dev-only credential; rotate or disable in production.

### DSN injection end-to-end

1. `app/compose/error-tracking.yml` runs the Bugsink container.
2. `app/compose/app.yml` passes `SENTRY_DSN: "${BUGSINK_DSN:-}"` to the Spring app container.
3. `application.yml` binds `sentry.dsn: ${SENTRY_DSN:}` — when empty, the SDK is inert (no network calls, no-op).
4. On first Bugsink boot, create a project via the UI (`http://localhost:8000`) and copy the DSN from the project settings.
5. Set `BUGSINK_DSN` in your environment or `.env` file and restart the app.

### What gets captured

Two call sites capture exceptions to Sentry:

- **`DefaultDslExceptionMapper`** — catches DSL exceptions and unhandled errors from REST endpoints. Sets a `runId` tag (from the `DslException.runId()` or the request attribute) before calling `Sentry.captureException()`.
- **`TemporalDslProcessService.propagateRunId()`** — sets the `runId` tag on the current Sentry scope for every Temporal run, so any exception captured during workflow execution carries the run id.

Both sites guard with `try/catch (Exception ignored)` — Sentry is optional and unconfigured SDK calls are safe no-ops.

### Correlating a `dsl_runs` run id to a Bugsink event

1. Note the `run_id` from the failing run (app log, `dsl_runs` table, or API response).
2. In Bugsink UI, search events by the `runId` tag: the tag is set on every captured exception.
3. Alternatively, grep the app log for the run id — Sentry breadcrumbs (when `minimum-breadcrumb-level: debug`) appear in the log as well.

### Verifying the integration

```bash
# 1. Confirm Sentry is active at startup (look for the startup log line):
docker compose -f app/docker-compose.yml logs app | grep -i "sentry"

# 2. Trigger an exception via the ExceptionProbe DSL (preview mode):
curl -sS -X POST http://localhost:8090/api/dsl/preview/ExceptionProbe \
  -H 'Content-Type: application/json' \
  -d '{"body": {"shouldFail": true, "reason": "integration test"}}'

# 3. Check Bugsink for the event:
curl -sS http://localhost:8000/api/0/organizations/default/issues/ \
  -H "Authorization: Bearer <your-auth-token>" | jq
```

**Note:** Bugsink requires manual project/DSN setup through the UI on first boot. The Sentry SDK cannot auto-create projects. If `SENTRY_DSN` is empty, the SDK is inert and no events are sent.

## Schedule a definition

Attach a Temporal Schedule to a published DSL definition so the engine fires it on a cron and
each fire produces a fresh `dsl_runs` row. Backed by
[`DslScheduleService`](../../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/service/DslScheduleService.java)
via the routes registered in
[`DslScheduleRouterConfiguration`](../../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/router/DslScheduleRouterConfiguration.java);
architecture detail in [§ Scheduling](architecture-backend.md#scheduling).

Use the BFF paths (`/api/v1/dsl/schedules*`) so the browser's `X-Api-Key` / JWT / RBAC posture
is applied consistently. The backend at `:8090` exposes the same routes at `/api/dsl/schedules*`
and behaves identically; pick whichever your client can reach.

### Create a schedule

```bash
curl -sS -X POST http://localhost:3000/api/v1/dsl/schedules \
  -H 'Content-Type: application/json' \
  -d '{
    "definition": "monthly-closing",
    "cron": "0 9 * * *",
    "timezone": "Europe/Vienna",
    "input": {"batchSize": 500},
    "note": "monthly close of books"
  }'
```

Returns `201 Created` with `{"scheduleId":"sched-monthly-closing","definition":"monthly-closing","cron":"0 9 * * *"}`.

Fields:

- `definition` (required) — must be published in the DSL module and match `^[A-Za-z0-9._-]{1,120}$`.
- `cron` (required) — standard 5-field Unix cron (`minute hour dom month dow`); the SDK parses
  this. See [§ Scheduling / Temporal ScheduleSpec cron format](architecture-backend.md#temporal-schedulespec-cron-format)
  for the precise contract.
- `timezone` (optional) — IANA zone id, default `UTC`.
- `input` (optional) — JSON object passed as the workflow input on every fire; default `{}`.
- `note` (optional) — free-form note attached to the schedule (returned by `list`).

Each fire produces one `dsl_runs` row with `triggered_by = NULL` (Temporal worker thread has no
HTTP/auth context — see [Known gaps](architecture-backend.md#known-gaps-not-currently-exposed--unclear)).

### List schedules

```bash
curl -sS 'http://localhost:3000/api/v1/dsl/schedules?limit=50&offset=0' | jq
```

Returns `PageResponse<ScheduleSummary>`: `{items: [...], total, offset, limit}`. Each item carries
`scheduleId`, `definition`, `cron`, `timezone`, `note`, `nextRunAt`, `paused`. Only schedules whose
id starts with `sched-` (those this service created) are listed — unrelated Temporal schedules are
filtered out by `listSchedules()`.

### Delete a schedule

```bash
curl -sS -X DELETE http://localhost:3000/api/v1/dsl/schedules/monthly-closing
```

Returns `200 {"deleted":true}`. Calling the schedule does **not** require the schedule to exist —
the service treats "not found" as success (`isNotFound(e)` check) so deletes are idempotent.

### Pause a schedule

```bash
curl -sS -X POST http://localhost:3000/api/v1/dsl/schedules/monthly-closing/pause -H 'Content-Type: application/json' -d '{"reason":"maintenance window"}'
```

Returns `200 {"paused":true}`. Resume with `POST /api/v1/dsl/schedules/monthly-closing/resume`.

### Common failure cases

| Symptom | HTTP | Cause | Fix |
|---|---|---|---|
| `400 BAD_REQUEST "definition is required"` / `"cron is required"` | 400 | Missing one of the required fields. | Include both in the JSON body. |
| `400 BAD_REQUEST "Invalid timezone: <id>"` | 400 | `timezone` is not a valid IANA `ZoneId`. | Use a real IANA id (e.g. `Europe/Vienna`, `America/New_York`); default is `UTC`. |
| `400 BAD_REQUEST "Invalid definition name: must match …"` | 400 | `definition` contains characters outside `^[A-Za-z0-9._-]{1,120}$` (e.g. spaces, `/`, accented letters). | Rename the definition in the DSL module and re-publish. |
| `404 NOT_FOUND "Definition … not found"` | 404 | `definition` is not in `GlobalManager.findGeneratedProcess(…)` — usually not yet published, or a typo. | Confirm `GET /api/dsl/definitions` lists it; publish via `POST /api/dsl/drafts/<name>/publish` or re-deploy the DSL module. |
| `409 CONFLICT` (schedule already exists) | 409 | A schedule for this definition already exists (id `sched-<definition>` is unique). | `DELETE /api/dsl/schedules/<definition>` first, then `POST` again. |
| `401 UNAUTHORIZED` | 401 | `cbs.dsl.auth.enabled=true` and `X-Api-Key` is missing/invalid (when going direct to backend). | Supply the configured `X-Api-Key`. The BFF forwards it via `proxyToBackend`. |
| `403 FORBIDDEN` mentioning `OPERATOR` | 403 | `cbs.dsl.auth.rbac.enabled=true` and the caller's role is `< OPERATOR` (e.g. `VIEWER`, `RUNNER`, `AUTHOR`). | Use a principal mapped to `OPERATOR` or `ADMIN` (API-key callers are `ADMIN`). |
| `500` / `503` from the backend, with `UNAVAILABLE` / `DEADLINE_EXCEEDED` in app logs | 5xx | Temporal is unreachable. The `createSchedule` call fails on gRPC. | Go to [incident #1 — Temporal disconnect](#1-temporal-disconnect--p0). |
| Schedule created but no `dsl_runs` rows appear at fire time | n/a | Either Temporal skipped the fire (overlap policy `SKIP` while the previous run was still going) or the workflow failed before reaching `startProcess`. | Check Temporal UI for the schedule's action history; check `dsl_runs` for `RUNNING` / `FAILED` rows; check app logs for workflow start failures. |

### Verifying a fired schedule

```bash
# 1. Confirm the next fire time
curl -sS http://localhost:3000/api/v1/dsl/schedules | \
  jq '.items[] | select(.definition=="monthly-closing") | {cron, timezone, nextRunAt, paused}'

# 2. After the fire, find the run row (no triggered_by — schedule has no caller identity)
docker exec -i $(docker ps -qf name=postgres) psql -U nova -d nova \
  -c "SELECT run_id, process_name, status, started_at, finished_at FROM dsl_runs \
      WHERE process_name='monthly-closing' AND triggered_by IS NULL \
      ORDER BY started_at DESC LIMIT 5;"

# 3. Trace the fire in Temporal UI — the Workflow id is "<scheduleId>-<scheduled-time>"
xdg-open http://localhost:8233  # or the URL behind your tunnel
```

## Publish via the approval gate

Use the two-person change-request approval gate when an `AUTHOR` cannot publish a draft on
their own — `cbs.dsl.approval.required=true` (off by default; when off, `POST
/api/v1/dsl/drafts/{name}/publish` behaves exactly like before). The gate is opt-in per deploy:
set `cbs.dsl.approval.required=true`, restart, and any direct publish from a caller below
`Role.OPERATOR` is rejected — see [§ Publish approval gate
(T568)](architecture-backend.md#observability--operations) for the property and the
audit-trail semantics. AUTHORs write the draft and submit a change request; a second
principal at `Role.AUTHOR`+ approves and the gate delegates publishing to the existing
`publishPayload` flow (same audit row, `actor=approver`).

Source: `ChangeRequestRouterConfiguration` (handler `ChangeRequestHandler`, service
`ChangeRequestService`, entity `ChangeRequestEntity`); RBAC table in
[`RbacAuthorizationFilter`](../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/security/RbacAuthorizationFilter.java).
BFF proxies under [`frontend/admin-ui-plugin/server/api/v1/dsl/drafts/[name]/change-request/`](../frontend/admin-ui-plugin/server/api/v1/dsl/drafts/)
(`change-request.post`) and
[`change-requests/[id]/approve.post`](../frontend/admin-ui-plugin/server/api/v1/dsl/change-requests/),
`/reject.post`. Use the BFF paths (`/api/v1/...`) so the browser's `X-Api-Key` / JWT / RBAC
posture is applied consistently; backend at `:8090` exposes the same routes at
`/api/dsl/...` and behaves identically.

### Submit the change request

An AUTHOR (or higher) snapshots the current draft into a `dsl_change_request` row
(`PENDING`); any prior `PENDING` row for the same definition is marked `SUPERSEDED`.

```bash
curl -sS -X POST http://localhost:3000/api/v1/dsl/drafts/LoanDsl/change-request
```

Returns `201 Created` with the `ChangeRequestEntity` body — fields: `id`,
`definitionName`, `draftContent` (the raw draft JSON snapshot, not surfaced by
default), `requestedBy`, `requestedAt`, `status:"PENDING"`, `approvedBy:null`,
`approvedAt:null`, `comment:null`. The submit always emits an audit row
`CHANGE_REQUEST_CREATE` (`actor` = current actor, `outcome` = `SUCCESS` / `FAILURE`,
target = `change-request:<id>`). **No request body** — the snapshot is read from the
draft file under `csb.dsl.source-dir/.workbench/drafts/<name>.json`.

### List pending change requests

```bash
curl -sS 'http://localhost:3000/api/v1/dsl/change-requests?definitionName=LoanDsl&status=PENDING' | jq
```

Returns `200 OK` with a `ChangeRequestEntity[]` list, newest first. Both query
parameters are optional and exact-match; `status` is one of
`PENDING | APPROVED | REJECTED | SUPERSEDED` (case-insensitive; an unknown value
returns `400 BAD_REQUEST` with `code:"INVALID_REQUEST"` and a message naming the
expected set). RBAC reads default to `Role.VIEWER`. Backend at `:8090`:
`/api/dsl/change-requests`.

### Approve and publish

A second principal at `Role.AUTHOR`+ approves the request — **never the requester**
(`ADMIN` is exempt). On success the service marks the row `APPROVED`, then delegates
to `DslDraftHandler.publishPayload` so the publish flow is not duplicated.

```bash
curl -sS -X POST http://localhost:3000/api/v1/dsl/change-requests/42/approve \
  -H 'Content-Type: application/json' \
  -d '{"comment":"looks good — banking hours"}'
```

Body is an optional `{comment}` record (`CommentRequest`); on approve the `comment`
is copied onto the row's `dsl_change_request.comment` column and returned on a
later `GET /api/dsl/change-requests`. On success the response is `200 OK` with the
`DraftResponse` from the publish path (`name`, `status:"Published"`, `location`,
`reloaded`, `loadResult`, `reloadError`, `diagnostics`, `savedAt` — same shape as a
direct `POST /publish`).

### Reject a change request

```bash
curl -sS -X POST http://localhost:3000/api/v1/dsl/change-requests/42/reject \
  -H 'Content-Type: application/json' \
  -d '{"comment":"breaking change — defer to Q4"}'
```

Returns `200 OK` with the rejected `ChangeRequestEntity`
(`status:"REJECTED"`, `comment` populated, `approvedBy:null`, `approvedAt:null`). Same
rank + self-approval guards as `approve`; **no publish** happens.

### Direct-publish behaviour under the gate

When `cbs.dsl.approval.required=true`, `POST /api/v1/dsl/drafts/{name}/publish` from a
caller below `Role.OPERATOR` returns immediately:

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "code": "FORBIDDEN",
  "message": "publish requires approval",
  "entityName": "LoanDsl",
  "runId": null,
  "correlationId": null,
  "exceptionId": null
}
```

ADMIN and OPERATOR callers bypass the gate (`operator.satisfies(OPERATOR)` is `true`).
The publish is still audited as `DEFINITION_PUBLISH` with `outcome:"FAILURE"` and
`details={"error":"publish requires approval"}`.

### Common failure cases

| Symptom | HTTP | Cause | Fix |
|---|---|---|---|
| `403 FORBIDDEN "publish requires approval"` (direct publish under gate) | 403 | `cbs.dsl.approval.required=true` and the caller is below `OPERATOR`. | Use the change-request flow above; an ADMIN/OPERATOR can still publish directly. |
| `404 NOT_FOUND` on `POST /change-request` | 404 | No draft file under `csb.dsl.source-dir/.workbench/drafts/<safe(name)>.json` for this definition. | Save a draft first (`POST /api/v1/dsl/drafts/{name}/save`), then re-submit. |
| `409 CONFLICT "csb.dsl.source-dir is not configured"` | 409 | `cbs.dsl.source-dir` is blank. | Set the property in `application.yml` and restart. The draft store has nowhere to look. |
| `403 FORBIDDEN` on submit | 403 | RBAC: submit requires `Role.AUTHOR`. | Re-authenticate as a principal at `AUTHOR+` (or use the API key, which `RoleResolver` maps to `ADMIN`). |
| `403 FORBIDDEN` on approve/reject with message "Change request requester cannot approve or reject their own request" | 403 | Caller is the requester and is not `ADMIN`. The gate is two-person by design. | Have a different principal approve, or escalate to `ADMIN`. |
| `403 FORBIDDEN` on approve/reject with message "Role AUTHOR is required to decide change requests …" | 403 | Caller rank `< AUTHOR` (VIEWER / RUNNER). | Use an `AUTHOR+` principal. The requester's rank is re-checked at the approve/reject boundary, not just at submit. |
| `404 NOT_FOUND "No change request: <id>"` | 404 | Unknown or already-deleted id (path-var `id` is `Long.parseLong`). | List first (`GET /change-requests`) and use the returned id. |
| `409 CONFLICT "Change request <id> is <status> and can no longer be decided"` | 409 | Already `APPROVED` / `REJECTED` / `SUPERSEDED`; the gate is single-decision. | No retry possible — open a new change request. Re-submitting with a fresh draft auto-supersedes the prior `PENDING`. |
| `400 INVALID_REQUEST "Invalid value for query parameter 'status': …"` | 400 | Typo on `?status=` (only `PENDING`/`APPROVED`/`REJECTED`/`SUPERSEDED` accepted). | Correct the value (case-insensitive). |
| `401 UNAUTHORIZED` (going direct to backend) | 401 | `cbs.dsl.auth.enabled=true` and the `X-Api-Key` is missing/invalid. The BFF forwards it via `proxyToBackend`. | Supply the configured `X-Api-Key`. |

### Verifying the audit trail

Every submit / approve / reject and every gated publish attempt writes a `dsl_audit`
row. Verify end-to-end:

```bash
# 1. List change requests after the round-trip
curl -sS 'http://localhost:3000/api/v1/dsl/change-requests?definitionName=LoanDsl' | jq

# 2. Inspect the audit log for that change-request id
docker exec -i $(docker ps -qf name=postgres) psql -U nova -d nova \
  -c "SELECT actor, action, target, outcome, details FROM dsl_audit \
      WHERE target LIKE 'change-request:42' \
      ORDER BY at DESC LIMIT 10;"

# 3. Confirm the publish itself happened (reloaded=true on the approve's response,
#    or a DEFINITION_PUBLISH SUCCESS row in dsl_audit with actor=<approver>)
docker exec -i $(docker ps -qf name=postgres) psql -U nova -d nova \
  -c "SELECT actor, action, target, outcome FROM dsl_audit \
      WHERE action='DEFINITION_PUBLISH' AND target='LoanDsl' \
      ORDER BY at DESC LIMIT 5;"
```

The approve path records `actor=approver` on the publish audit row (the original
requester never lands on the publish row). Migration `V1__init.sql` (postgres)
provides the `dsl_change_request` table and the supporting index; see the auth
posture in [§ Security / production profile](architecture-backend.md#production-secure-default-profile-t413)
for how RBAC interacts with the API-key and OIDC filters.

## Promote a definition between environments

Copy a bundle of published definition markers from one configured environment workbench
directory to another, without redeploying the DSL module. Backed by
[`DslPromoteHandler`](../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/controller/DslPromoteHandler.java)
(routes in
[`DslPromoteRouterConfiguration`](../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/router/DslPromoteRouterConfiguration.java),
logic in
[`DslDefinitionBundleService`](../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/service/DslDefinitionBundleService.java));
architecture detail in [§ Environment promotion (T569)](architecture-backend.md#environment-promotion-t569).
Part of the [Epic 5 — Authoring experience & DSL lifecycle](roadmap.md#epic-5--authoring-experience--dsl-lifecycle)
roadmap.

The bundle carries **metadata only** — published (and optionally draft) markers from
`.workbench/published` / `.workbench/drafts` under the source `basePath`. It does **not** contain
DSL source code; the corresponding generated `.java` files must be deployed to the target
separately. The target environment is **not** reloaded — it picks up its markers on its own reload
cycle. Apply **overwrites** existing target markers with the same name (published wins per name).

Use the BFF paths (`/api/v1/dsl/promote*`) so the browser's `X-Api-Key` / JWT / RBAC posture
is applied consistently. The backend at `:8090` exposes the same routes at `/api/dsl/promote*`
and behaves identically; pick whichever your client can reach.

### Prerequisites — configure the environments

Environments are opt-in and empty by default; until at least one is configured the promote
endpoints report `404 ENV_NOT_FOUND`. Each environment maps to a filesystem root holding the
standard `.workbench` marker layout (relative `basePath` values resolve against
`cbs.dsl.source-dir`):

```yaml
cbs:
  dsl:
    promotion:
      environments:
        dev:
          base-path: /srv/dsl/dev      # relative paths resolve against cbs.dsl.source-dir
        staging:
          base-path: /srv/dsl/staging
```

Config keys: `cbs.dsl.promotion.environments.<name>.base-path` (see
[`DslProperties.Promotion`](../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/properties/DslProperties.java)).
There is no Temporal namespace/cluster mapping — promotion moves markers between directories on
the shared filesystem (cross-host HTTP promotion is a follow-up). Related:
`cbs.dsl.bundles.require-digest` (default `false`) — when true, bundles without a digest are
rejected with `BUNDLE_DIGEST_MISSING`.

### List environments

```bash
curl -sS http://localhost:3000/api/v1/dsl/promote/environments | jq
```

Returns `200 OK` with a `PromotionEnvironment[]` — sorted names of the configured environments:

```json
[{"name":"dev"},{"name":"staging"}]
```

### List promotable definitions in a source environment

```bash
curl -sS 'http://localhost:3000/api/v1/dsl/promote/definitions?env=dev' | jq
```

Returns `200 OK` with a `PromotionDefinition[]` (`name`, `type`, `status`), sorted by name.
Returns `404 ErrorResponse` (`code:"ENV_NOT_FOUND"`) when `env` is unknown or its directory is
missing. RBAC reads default to `Role.VIEWER`; only `POST /api/dsl/promote` requires
`Role.OPERATOR` (see
[`RbacAuthorizationFilter`](../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/security/RbacAuthorizationFilter.java)).

### Dry-run the promotion

Always dry-run first: `?dryRun=true` computes the diff against the target without writing
anything.

```bash
curl -sS -X POST 'http://localhost:3000/api/v1/dsl/promote?dryRun=true' \
  -H 'Content-Type: application/json' \
  -d '{
    "source": "dev",
    "target": "staging",
    "definitions": ["monthly-closing"],
    "includeDrafts": false
  }' | jq
```

Body (`PromotionRequest`):

- `source` (required) — environment name to export from.
- `target` (required) — environment name to import into; must differ from `source`.
- `definitions` (optional) — subset of the source's definitions; absent/empty promotes everything.
- `includeDrafts` (optional) — also consider draft markers on the source (published wins per name).

Returns `200 OK` with an `ImportBundleResult` (`dryRun:true`). `published` counts entries that
would change (`created`/`updated`); `failed` counts `skipped` (invalid) entries — nothing was
written and no reload happened (`reloaded:false`):

```json
{
  "dryRun": true,
  "reloaded": false,
  "published": 2,
  "failed": 0,
  "results": [
    {"name": "LoanDsl", "outcome": "created"},
    {"name": "monthly-closing", "outcome": "updated", "message": "definition differs (±128 bytes)"}
  ]
}
```

Diff outcomes: `created` (no target marker), `unchanged` (canonical equal — not counted in
`published`), `updated` (differs, or existing marker unreadable and will be overwritten),
`skipped` (invalid entry: missing or blank name).

### Apply the promotion

Repeat the same call without `?dryRun=` (default `false`). Each entry is snapshotted in the
target's history, applied (status forced to `Published`), and re-verified against the bundle
digest; per-entry failure yields `outcome:"failed"` without aborting the rest.

```bash
curl -sS -X POST http://localhost:3000/api/v1/dsl/promote \
  -H 'Content-Type: application/json' \
  -d '{
    "source": "dev",
    "target": "staging",
    "definitions": ["monthly-closing"],
    "includeDrafts": false
  }' | jq
```

Returns `200 OK` with `dryRun:false`; `published` counts entries with `outcome:"published"`,
`failed` counts per-entry failures:

```json
{
  "dryRun": false,
  "reloaded": false,
  "published": 1,
  "failed": 0,
  "results": [
    {"name": "monthly-closing", "outcome": "published"}
  ]
}
```

`reloaded` stays `false` — the target environment is not reloaded by this call.

### Common failure cases

| Symptom | HTTP | Cause | Fix |
|---|---|---|---|
| `400 INVALID_REQUEST "malformed promotion request JSON"` | 400 | Body is not valid JSON. | Fix the request body. |
| `400 INVALID_REQUEST "source and target are required"` | 400 | Blank `source` or `target`. | Set both in the body. |
| `400 INVALID_REQUEST "source and target must differ"` | 400 | `source == target`. | Pick two distinct environments. |
| `404 ENV_NOT_FOUND "environment not configured: <name>"` | 404 | `source`, `target`, or the `?env=` param names an unknown environment. | List environments (`GET /api/v1/dsl/promote/environments`) and use a configured name; add it to `cbs.dsl.promotion.environments` otherwise. |
| `404 ENV_NOT_FOUND "environment directory does not exist: <dir>"` | 404 | The environment's `base-path` directory is missing on the filesystem. | Create the directory (with the `.workbench` layout) or fix `base-path`. |
| `400 BAD_REQUEST "BUNDLE_DIGEST_MISSING"` / `"BUNDLE_DIGEST_MISMATCH"` | 400 | Bundle digest verification failed (digest also enforced when `cbs.dsl.bundles.require-digest=true`). | Re-export/retry; on apply, mismatch means the target markers do not match the bundle — investigate concurrent writes. |
| `403 FORBIDDEN "Role OPERATOR is required for POST /api/dsl/promote …"` | 403 | `cbs.dsl.auth.rbac.enabled=true` and the caller's role is `< OPERATOR` (e.g. `VIEWER`, `RUNNER`, `AUTHOR`). | Use a principal mapped to `OPERATOR` or `ADMIN` (API-key callers are `ADMIN`). |
| `401 UNAUTHORIZED` (going direct to backend) | 401 | `cbs.dsl.auth.enabled=true` and the `X-Api-Key` is missing/invalid. | Supply the configured `X-Api-Key`. The BFF forwards it via `proxyToBackend`. |
| Apply reports `failed` entries | 200 | Per-entry apply failure (e.g. marker write error); the rest of the bundle still applied. | Read the entry's `message`; fix the cause and re-apply. |

### Verifying the promotion audit trail

Every apply (dry-run included) writes a `dsl_audit` row with `action="PROMOTION"`,
`target=<target environment>`, and details `{source, target, definitions, digest, count}` on
success or `{source, target, error}` on failure:

```bash
# 1. Confirm the target markers landed
curl -sS 'http://localhost:3000/api/v1/dsl/promote/definitions?env=staging' | jq

# 2. Inspect the audit log for the target environment
docker exec -i $(docker ps -qf name=postgres) psql -U nova -d nova \
  -c "SELECT actor, action, target, outcome, details FROM dsl_audit \
      WHERE action='PROMOTION' AND target='staging' \
      ORDER BY at DESC LIMIT 10;"
```

Prefer the UI? The Promote page (`/nova-admin`) previews the same diff and applies it in one
flow — see [§ Promote page](architecture-ui.md#promote-page-t569) for the page contract.

## Signal a running process

Deliver a Temporal signal to a running DSL workflow that declared it, and read back
the buffered payloads via the runtime query. The DSL author declares the signal with
`ProcessBuilder.signal(name, payloadType)`; the generator emits an `@SignalMethod` and
a sibling `@QueryMethod dslSignalState()` on the workflow interface, both routed through
the same RBAC / auth / OIDC stack as run and preview. Full design in [§ Signals
(T567)](architecture-backend.md#signals-t567); the integration test
[`SignalProbeDslIntegrationTest`](../backend/dsl-starter/starter/src/integrationTest/java/cbs/nova/dsl/example/integration/SignalProbeDslIntegrationTest.java)
exercises both paths end-to-end.

Source: `DslSignalsHandler` + `DslSignalService`, routes mounted by
`DslSignalsRouterConfiguration`. BFF proxies:

- `POST /api/v1/dsl/signals/{runId}` → [`frontend/admin-ui-plugin/server/api/v1/dsl/signals/[runId].post.ts`](../frontend/admin-ui-plugin/server/api/v1/dsl/signals/[runId].post.ts)
  (`json: true`).
- `GET /api/v1/dsl/queries/{runId}` → [`frontend/admin-ui-plugin/server/api/v1/dsl/queries/[runId].get.ts`](../frontend/admin-ui-plugin/server/api/v1/dsl/queries/[runId].get.ts)
  (`json: false`, raw-state pass-through).

Backend at `:8090` exposes the same routes at `/api/dsl/signals/{runId}` and
`/api/dsl/queries/{runId}`; pick whichever your client can reach. The browser must
never be pointed at Spring Boot directly — CORS + token exposure.

### Declare the signal in the DSL

The process must declare the signal it wants to await; otherwise the generated
`@SignalMethod` is missing and Temporal surfaces a generic RuntimeException as `5xx`.
The full example lives at
[`SignalProbeDsl`](../backend/dsl-starter/dsl-examples/src/dsl/SignalProbeDsl.java);
minimum shape:

```java
import cbs.nova.dsl.Dsl;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("SignalProbe")
          .input(String.class)
          .output(String.class)
          .signal("approval", Map.class)
          .execute(ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload =
                (Map<String, Object>) ctx.awaitSignal("approval", Map.class);
            return Result.success(payload.get("approved") + ":" + payload.get("reviewer"));
          })
          .buildList();
}
```

`ProcessBuilder.signal(name, payloadType)` declares; `ctx.awaitSignal(name, type)`
blocks on Temporal's `Workflow.await`. Three await helpers exist on
`ProcessContext` — `awaitSignal` (blocking, throws if no awaiter is bound),
`getSignalPayload` (nullable peek), `signalReceived` (boolean check) — and all three
degrade gracefully in preview because no `SignalBuffer` is installed. Preview-mode
signals are **inert**: a `signal(...)` declaration is ignored by preview / hierarchy /
explain; only a real Temporal run reaches the dispatcher.

### Send the signal

Start a run first (`POST /api/v1/dsl/run/SignalProbe`, captured in the Executions
list), capture the runId, then deliver:

```bash
curl -sS -X POST http://localhost:3000/api/v1/dsl/signals/<runId> \
  -H 'Content-Type: application/json' \
  -d '{
        "signalName": "approval",
        "payload": {"approved": true, "reviewer": "alice"}
      }'
```

Body shape (`DslSignalsHandler.SignalRequest`): `signalName` (required, string),
`payload` (optional — Jackson treats an absent field as `null`, and the handler
forwards to `WorkflowStub.signal(name)` when no payload is provided). On success
returns `200 OK` with the inline envelope:

```json
{
  "runId": "<runId>",
  "signalName": "approval",
  "status": "sent"
}
```

`"sent"` means Temporal *accepted* the signal — **not** that the workflow has
observed it yet (`Workflow.await` unblocks on the next workflow task turn). For
guaranteed at-least-once on the workflow side, follow up with the query below.

### Read back the buffered payloads

```bash
curl -sS http://localhost:3000/api/v1/dsl/queries/<runId> | jq .signalState
```

Returns `200 OK` with:

```json
{
  "runId": "<runId>",
  "signalState": {
    "approval": {"approved": true, "reviewer": "alice"}
  }
}
```

Each entry is the last payload captured by the generated `@SignalMethod` handler —
the per-name snapshot, not a queue. The query method is `WorkflowStub.query(
"dslSignalState", Map.class)`; an empty `Map.of()` is returned on a Temporal
disconnect or closed-workflow error and the WARN is logged (`DslSignalService`).

### RBAC notes

- `POST /api/dsl/signals/**` → `Role.RUNNER` (RBAC filter; see
  [`RbacAuthorizationFilter`](../backend/dsl-starter/starter/src/main/java/cbs/nova/starter/security/RbacAuthorizationFilter.java) line 52).
- `GET /api/dsl/queries/**` → no explicit rule; reads default to `Role.VIEWER`
  (RBAC filter line 131).
- API-key callers are mapped to `Role.ADMIN` by `RoleResolver`, so an API key
  satisfies both routes regardless of the explicit rule.
- The `POST /api/dsl/signals/**` route is **not** in `RateLimitFilter.RULES` — send is
  not currently rate-limited; reads are exempt anyway.

### Common failure cases

| Symptom | HTTP | Cause | Fix |
|---|---|---|---|
| `404 NOT_FOUND "Execution run not found: <id>"` on `POST /signals/{runId}` | 404 | The runId has no row in `dsl_runs` (typo or never submitted). | List runs (`GET /api/v1/executions`); the runId is `run-<UUID>` per `SimpleContext.generateRunId()`. |
| `409 CONFLICT "Execution run is not running: <id> (status COMPLETED)"` (or `FAILED` / `STALE` / `CANCELLED`) | 409 | The run is terminal — signals are only delivered to `RUNNING` workflows. | Nothing to do against this runId. Start a fresh run; signals are bound to a single execution. |
| `404 NOT_FOUND "Execution run not found: <id>"` on `GET /queries/{runId}` | 404 | Same as above — unknown runId. | Same as above. |
| `200 OK {"runId": "...", "signalState": {}}` on the query | 200 | Run is registered but not currently `RUNNING` (Temporal-side termination isn't reflected yet in `dsl_runs.status`) **or** the workflow never declared any signals (the handler still emits the `@QueryMethod` so the empty map is the only state). | Confirm `dsl_runs.status='RUNNING'`; if it is, the codegen fell through cleanly with no declared signals — check the DSL source. |
| `5xx` with `"SignalNotRegistered"` (or similar Temporal RuntimeException) in the response body | 5xx | The signal name was not declared in the process — `@SignalMethod <name>` does not exist on the generated workflow. | Only send signal names listed by `ProcessBuilder.signal(...)` for that definition; check the DSL source. The FE panel sends whatever name the operator types; the runbook says "follow the DSL". |
| `5xx` "Internal Server Error" with no client message | 5xx | Temporal disconnect — `WorkflowClient.newUntypedWorkflowStub` threw on `signal(...)` and the handler rethrew. The WARN is logged. | Restore Temporal (incident #1) and retry; the same runId picks up because signals are addressed by workflow id. |
| `400 "Invalid signal request body: …"` | 400 | Body is not the expected `SignalRequest{ signalName, payload? }` shape — `signalName` absent, or the JSON itself is malformed. | Reshape as `{"signalName":"<name>","payload":{...}}` (payload optional). |
| `401 UNAUTHORIZED` (going direct to backend) | 401 | `cbs.dsl.auth.enabled=true` and the `X-Api-Key` is missing/invalid. The BFF forwards it via `proxyToBackend`. | Supply the configured `X-Api-Key`. |
| `403 FORBIDDEN` mentioning `RUNNER` (send route) | 403 | `cbs.dsl.auth.rbac.enabled=true` and the caller is `VIEWER`. | Re-authenticate as `RUNNER+`, or use the API key (`ADMIN`). |
| `403 FORBIDDEN` mentioning `VIEWER` (query route) | 403 | RBAC defaults reads to `VIEWER`; `VIEWER` satisfies this. If seen, an upstream filter (api-key or OIDC) is failing first. | Check the `X-Api-Key` or JWT before assuming RBAC. |

### Verifying a signal was observed

```bash
# 1. The run row stays RUNNING (or transitions to COMPLETED if the signal unblocked
#    and the workflow ended). Confirm status, then look up the buffered payload:
curl -sS http://localhost:3000/api/v1/dsl/queries/<runId> | \
  jq '.signalState.approval'

# 2. Tail app logs for the signal trace:
docker compose -f app/docker-compose.yml logs app | \
  grep -E "Sent signal approval to run <runId>|Failed to send signal"

# 3. Temporal UI (Workflow id = "<runId>") shows the signal event in the workflow
#    history between the start and the await unblock — useful when the response said
#    "sent" but the workflow never observed it (race against Workflow.await unblock).
xdg-open http://localhost:8233  # search by workflow id == runId
```

A successful round-trip: send returns `"status":"sent"` **and** the query returns the
buffered payload. A `"sent"` without a follow-up query is a weak guarantee — the
workflow unblocks on the next task turn, which can be hundreds of milliseconds later
under load. For long-lived awaits prefer polling the query rather than relying on the
send response alone.

## Maintaining this runbook

Every new ops-relevant change (a scheduled job, a new failure mode, a new external dependency)
adds an entry here in the same six-part shape. Keep triage commands copy-paste-ready; keep
internal class names out unless an operator must grep for them.
