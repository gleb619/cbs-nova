# cbs-nova — Roadmap

High-level, domain-drilled roadmap. Each entry is a large epic ("big idea") that groups many
concrete changes. It is deliberately coarser than [`kanban.md`](kanban.md); individual tasks and
acceptance criteria still live under [`plans/`](plans). Companion to
[`architecture-backend.md`](architecture-backend.md) and [`architecture-ui.md`](architecture-ui.md).

Status vocabulary matches the kanban board (`Backlog` / `Ready` / `In Progress` / `Done`).

---

## Epic 1 — BFF ↔ API contract & communication

**Problem.** The BFF proxies Spring Boot through hand-written, per-path Nitro files with a manual
header allowlist. Every new backend DSL route needs a matching proxy route added by hand
(CLAUDE.md caveat). There is no shared schema, no generated client, and drift is caught only by
the T335 contract tests after the fact.

**Target state.** One source of truth for the HTTP surface; the BFF layer is mostly generated or
schema-driven; contract drift fails the build.

### Workstreams

| Domain | Changes |
|---|---|
| Schema source of truth | Publish a complete OpenAPI 3.1 document from the Spring Boot side (springdoc is already present). Treat it as a build artifact consumed by the frontend. |
| Generated BFF client | Generate the typed backend client (`server/utils/httpClient` callers) from the OpenAPI doc instead of hand-writing `useXxxApi` composables. Kill the "add a matching proxy route" manual step — derive proxy routes from a route manifest. |
| Contract enforcement | Promote T335 BFF↔backend contract tests into a required CI gate. Add schema-diff check: a breaking change to a backend DTO fails CI unless the OpenAPI version bumps. |
| Error envelope unification | Single `ErrorResponse` / `PreviewErrorDetail` shape across run/preview/explain/executions/drafts. BFF passes it through verbatim; frontend has one error renderer (`ErrorsTab`). |
| Header & correlation plumbing | Formalize the pass-through allowlist (`Authorization`, `X-Api-Key`, `X-Request-Id`, `traceparent`, `Idempotency-Key`, `X-Correlation-Id`) as shared config, not a literal list in `proxyToBackend`. Propagate `traceparent` end-to-end (browser → BFF → backend → DSL execution). |
| Streaming / long-poll | Replace `useStalePolling` fixed-interval polling with SSE or long-poll from the BFF for run status and dry-run logs. |
| Pagination convention | One `{items, total, offset, limit}` envelope for every list endpoint (executions, drafts, definitions, schedules). |

---

## Epic 2 — Security & access management

**Problem.** Three independent opt-in guards (API-key filter, in-memory rate limiter, OIDC
resource-server), all off by default. No authorization model beyond "authenticated": any valid JWT
can run, cancel, publish, delete. Rate limiter is per-instance in-memory. No audit trail of who did
what. BFF holds tokens but there is no RBAC mapping.

**Target state.** Secure-by-default deployment profile; role-based authorization on mutating routes;
tamper-evident audit log; horizontally-safe rate limiting.

### Workstreams

| Domain | Changes |
|---|---|
| Authorization model | Define roles (`viewer`, `runner`, `author`, `operator`, `admin`). Map JWT scopes/claims → roles. Enforce per-route: `run`/`cancel` = runner, `drafts/publish` + `reload` = author, `schedules` + retention = operator, config = admin. |
| Secure default profile | A `production` Spring profile that turns OIDC on, requires the API key for service-to-service, and enables rate limiting — so a bare deploy is not anonymous. |
| Audit logging | Append-only `dsl_audit` table: actor (`triggered_by`), action, target, correlation id, timestamp, outcome. Surface it as `GET /api/audit` and an Executions-page tab. |
| Distributed rate limiting | Move the token bucket to a shared store (Redis / Postgres) keyed by principal + route class, so limits hold across replicas. Keep the in-memory impl as the dev fallback. |
| Secrets & key management | Rotate-able API keys (multiple active keys, labelled, revocable) instead of a single `dsl.auth.api-key` string. Externalize to a secret store. |
| BFF session hardening | CSRF protection on the OIDC callback/logout GET routes, session-cookie rotation on refresh, configurable idle/absolute session lifetime, `Secure` enforced behind TLS-terminating proxy. |
| DSL execution sandboxing | Constrain what helpers/functions can reach at runtime (network, filesystem) via a capability allowlist declared per DSL module; deny by default in preview. |
| Supply chain | SBOM generation + dependency CVE gate in CI for both Gradle and pnpm trees. |

---

## Epic 3 — Events, notifications & webhooks

**Problem.** The engine is request/response only. Nothing can subscribe to "run finished",
"run failed", "draft published", "schedule fired". `useStalePolling` exists precisely because there
is no push channel. Operators learn about failures by looking.

**Target state.** A first-class domain-event stream that the UI, external systems, and operators can
subscribe to; pluggable notification sinks.

### Workstreams

| Domain | Changes |
|---|---|
| Domain event model | Typed events: `RunStarted/Completed/Failed/Cancelled/Stale`, `CompensationTriggered`, `DraftSaved/Published`, `ScheduleFired`, `ReloadFailed`. Stable JSON schema, versioned. |
| Event store & outbox | Transactional outbox on `dsl_runs` writes → durable event log table. Replay + at-least-once delivery. |
| Delivery channels | (a) SSE endpoint for the admin UI; (b) outbound webhooks with HMAC signing, retry/backoff, dead-letter; (c) optional message-queue publisher (the external-call taxonomy already names `mq`). |
| Notification rules | Operator-configured rules: "on `RunFailed` for process X, notify channel Y". Sinks: email, Slack/webhook, PagerDuty-style. |
| UI integration | Live Executions page (no polling), a notification bell / activity feed, per-user subscription preferences. |
| Temporal signals bridge | Map Temporal workflow signals/queries onto the DSL surface so a running Process can receive external events, not just emit them. |

---

## Epic 4 — Observability, diagnostics & operability

**Problem.** Metrics (Micrometer), tracing (OTel, opt-in), and health checks exist but are
disconnected. No dashboards ship. Tracing is no-op unless an OTLP endpoint is set. Compile
diagnostics are capped at 20 and only returned inline. No SLO definitions. Retention purger and
orphan purge are separate ad-hoc mechanisms.

**Target state.** Turnkey observability — ship dashboards, wire traces through every layer, make
failures self-describing.

### Workstreams

| Domain | Changes |
|---|---|
| Shipped dashboards | Grafana dashboards + Prometheus scrape config in `app/compose/` for the existing `dsl.run.*` / `dsl.preview.*` meters. Alert rules for error rate, preview timeout rate, Temporal unreachable. |
| End-to-end tracing | Default-on tracing in the compose stack (bundled collector). Spans across BFF → backend → DSL dispatch → Temporal activity, correlated with `correlation_id`. |
| Structured diagnostics | Persist compile diagnostics (not just inline, not just 20). A Workbench "diagnostics history" view. Machine-readable diagnostic codes. |
| SLOs & health | Define SLOs (run success %, p95 run latency, preview latency). Expand `/actuator/health` detail; readiness vs liveness split. |
| Unified retention/GC | Fold `DslRunRetentionPurger`, orphan purge, and audit-log retention into one scheduled maintenance service with shared config and metrics. |
| Log correlation | Ensure `rid` / `correlation_id` is on every log line in backend and BFF; document the query recipe. |

---

## Epic 5 — Authoring experience & DSL lifecycle

**Problem.** DSL edits go through drafts → publish → `reload`, but versioning is coarse ("every
instance runs on the version it started with" is a runtime guarantee, not an authoring feature).
Workbench autosave is single-tab `localStorage` with a 24h TTL — real edits can be lost. Export/import
(T319) exists but there is no diff/review/approval gate before publish. No environments (dev → staging
→ prod promotion).

**Target state.** DSL definitions are versioned, reviewable, and promotable artifacts with a real
change-management workflow.

### Workstreams

| Domain | Changes |
|---|---|
| Server-side draft persistence | Move drafts off browser `localStorage` into the backend, per-user, with multi-tab sync and no TTL data loss. Optimistic-lock on concurrent edits. |
| Versioned definitions | First-class version history per construct: view, diff (reuse `ExplainDiffView` / `ASTDiffNode`), roll back. Tie run attribution to the exact definition version hash. |
| Review / approval gate | Optional "publish requires approval" mode: a draft becomes a change request, a second principal (author/operator role from Epic 2) approves, then it publishes. Audit-logged. |
| Environment promotion | Use the T319 bundle export/import as the promotion mechanism: signed bundles, dry-run diff against the target environment before apply, one-click promote dev → staging → prod. |
| DSL testing surface | Author-defined example inputs + expected outputs stored with the definition; a "run the definition's own test cases" button in preview mode; CI hook to run them on every bundle. |
| Editor depth | Schema-aware autocomplete for helper/function references (feed from `/api/dsl/objects/search`), inline compile diagnostics, and the DSL helpers cookbook (T334) surfaced in-editor. |

---

## Sequencing notes

- **Epic 1** unblocks 3, 4, and 5 (shared schema + error envelope + correlation plumbing), do it first.
- **Epic 2** authorization roles are a prerequisite for the Epic 5 approval gate and the Epic 3
  notification-rule admin surface — land the role model early even if enforcement rolls out per-route.
- **Epic 3** and **Epic 4** share the correlation-id / outbox plumbing; build the event store once.
- **Epic 5** is the largest surface area but the least blocking for other teams — it can run in
  parallel once Epic 1 lands.
