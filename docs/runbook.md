# Operator Incident Runbook

First-response playbook for cbs-nova production incidents. Each entry is **Symptom → Triage →
Hypotheses → Mitigation → Permanent fix → Verification**. Follow the steps top to bottom; the
goal is operator-mitigates-in-under-5-minutes.

**Scope:** operational level only — no code-level debugging, no internal class names beyond what
an operator needs to grep a log. For architecture, see
[`architecture-backend.md`](architecture-backend.md) (security & ops layer) and
[`dsl/configuration.md`](dsl/configuration.md) (every property named here).

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

## Maintaining this runbook

Every new ops-relevant change (a scheduled job, a new failure mode, a new external dependency)
adds an entry here in the same six-part shape. Keep triage commands copy-paste-ready; keep
internal class names out unless an operator must grep for them.
