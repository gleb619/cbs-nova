# Operations Pages

The Operations pages are the six screens operators use to manage the day-to-day of a deployed DSL stack: scheduling cron fires, triaging approval-gated publishes, promoting bundles between environments, auditing webhook deliveries, maintaining notification rules, and reading the engine's domain-event feed. None of them render DSL source — that lives on the [Workbench](./dsl-workbench.md) and [Runner](./runner.md); they sit one layer above, wiring operators to the engine's control-plane endpoints.

The page-level contracts below mirror the route/BFF breakdown in [`../architecture-ui.md`](../architecture-ui.md); this document adds the screen-level view: what the operator sees, the inputs they touch, the actions they fire, and the states the UI can be in. Layout, sidebar nav, and top-bar widgets (including the notification bell and saved-drafts menu) live in [`./layout.md`](./layout.md).

The six pages, in nav order:

| Page | Route | Source | Purpose |
|------|-------|--------|---------|
| Schedules | `/schedules` | `frontend/admin-ui-plugin/app/pages/schedules.vue` | Temporal cron schedules keyed by a published DSL definition. |
| Activity | `/activity` | `frontend/admin-ui-plugin/app/pages/activity.vue` | Append-only domain-event feed (runs, drafts, reloads), newest first. |
| Webhooks | `/webhooks` | `frontend/admin-ui-plugin/app/pages/webhooks.vue` | Delivery outcomes for run-completion webhook subscriptions. |
| Notifications | `/notifications` | `frontend/admin-ui-plugin/app/pages/notifications.vue` | Rules that match domain events and fan out to webhook/email/Slack/PagerDuty sinks. |
| Approvals | `/approvals` | `frontend/admin-ui-plugin/app/pages/approvals.vue` | Inbox for workbench-submitted change requests awaiting an approver. |
| Promote | `/promote` | `frontend/admin-ui-plugin/app/pages/promote.vue` | Move published definition bundles between environments. |

## Schedules

The Schedules page is the operator-facing surface over Temporal's "start a workflow on a cron" feature. Each row binds a published DSL definition to a cron expression and timezone; rows can be paused and resumed, deleted, and created from an inline form.

### User story

> As an operator, I want to bind a published DSL definition to a Temporal cron schedule so the engine fires it on a recurring schedule without a human pressing Run, and I want to pause, resume or delete that schedule from one screen.

### What the page shows

The page renders the `DslScheduleList` panel from `@cbs/components` (`frontend/components/src/components/dsl/ScheduleList.vue`). The page itself owns only the header and forwards state/events; the panel owns the UI.

Header:

- **"Schedules"** title with subtitle "Cron-driven Temporal schedules that start a published DSL workflow."

Create form (inline above the list, `data-testid="schedule-create-form"`):

| Field | Label | Placeholder | Test id |
|-------|-------|-------------|---------|
| Definition | "Definition" | `LoanDisbursement` | `schedule-definition-input` |
| Cron | "Cron" | `0 9 * * *` | `schedule-cron-input` |
| Timezone | "Timezone" | — (input `UTC`) | `schedule-timezone-input` |
| Note | "Note" | — | `schedule-note-input` |
| Input JSON (optional) | "Input JSON (optional)" | `{"amount": 100}` | `schedule-input-textarea` |

Submit button (`schedule-create-submit`) is disabled until both `Definition` and `Cron` are non-blank. An inline error (`schedule-input-error`) renders when the JSON textarea is rejected.

List (`schedule-list`):

- **Loading** — `schedule-list-loading` skeleton rows.
- **Error** — `schedule-list-error` message.
- **Empty** — `schedule-list-empty`: "No schedules configured."
- **Row** (`schedule-row`) — Definition (with a small `paused` pill when `schedule.paused === true`), `cron · timezone · next <nextRunAt>` line, optional note line, a pause/resume icon button (`schedule-pause-toggle`, title flips between "Pause schedule" / "Resume schedule"), and a delete button (`schedule-delete`). Delete is a two-step inline confirm (`schedule-delete-confirm-group`, `schedule-delete-confirm`, `schedule-delete-cancel`).

### States

- **Loading** — skeleton rows under the header.
- **Empty** — "No schedules configured." text under the create form.
- **Error** — backend error string in red under the create form.
- **Pausing in flight** — the row's pause/resume button is disabled via `pausing[definition]`; other rows remain interactive.
- **Delete confirm** — second row slot replaces the action buttons with Confirm / Cancel.

### BFF routes

OpenAPI-generated and mounted by the BFF handler — see the Schedules page section of [`../architecture-ui.md`](../architecture-ui.md#schedules-page):

- `GET /api/v1/dsl/schedules`
- `POST /api/v1/dsl/schedules`
- `DELETE /api/v1/dsl/schedules/{definition}`
- `POST /api/v1/dsl/schedules/{definition}/pause`
- `POST /api/v1/dsl/schedules/{definition}/resume`

After every mutation `useSchedules` re-runs `load()` and fires `notifySchedulesChanged()` so other surfaces (e.g. the Workbench publish action) can invalidate their cache. The full backend semantics (overlap policy, schedule-action payload, failure cases) are in [`../runbook.md`](../runbook.md#schedule-a-definition).

## Activity

The Activity page is the engine's append-only domain-event feed: runs, drafts and reloads, newest first. It is the counterpart to the backend `GET /api/dsl/events` audit surface — see [`../architecture-ui.md`](../architecture-ui.md#activity-page).

### User story

> As a support engineer, I want to scroll an append-only feed of every domain event the engine emitted so I can correlate a user report with a Temporal run, a draft save, or a piece reload without grepping logs.

### What the page shows

The page renders the `ActivityFeed` panel from `@cbs/components` (`frontend/components/src/components/ActivityFeed.vue`) inside a single white card. Page-level test id: `activity-page`.

Known event types surfaced in the `Event type` filter datalist:

- `RunStarted`, `RunCompleted`, `RunFailed`, `RunCancelled`, `RunStale`
- `DraftSaved`, `DraftPublished`
- `PieceNotified`

Top filter row (sticky under the header):

| Filter | Placeholder | Test id |
|--------|-------------|---------|
| Event type (text + datalist) | "Event type" | `activity-filter-type` |
| Aggregate ID (form + Apply) | "Aggregate ID" | `activity-filter-aggregate`, `activity-filter-aggregate-apply` |
| Correlation ID (form + Apply + Clear) | "Correlation ID" | `activity-filter-correlation`, `activity-filter-correlation-apply`, `activity-filter-correlation-clear` |
| Since (timestamp) | — | `activity-filter-since` |

`Event type` is grouped by leading word (`run` / `draft` / `piece`) for the badge colour class. Row-level badge test id: `activity-row`.

The page wires three props into the panel:

- `fetchEvents` — the BFF query function from `useDslApi`.
- `executionLink(aggregateId)` — always returns `/executions/{aggregateId}`.
- `linkComponent` — the resolved `NuxtLink` so the panel can deep-link into the Executions detail view without coupling to Nuxt.

### States

- **Loading** — first page is fetched on mount via `load(0)`; no auto-polling (Phase 1 is pull-only, manual refresh).
- **Empty** — empty-state message when the backend returns no events for the current filter set.
- **Error** — backend error string from the fetch function, surfaced by the panel.
- **Paging** — `Prev` / `Next` pager driven by the panel's own cursor.

### BFF routes

Hand-written proxy, spec-covered (backend `GET /api/dsl/events` is present in `docs/openapi.json`):

- `GET /api/v1/dsl/events` — `frontend/admin-ui-plugin/server/api/v1/dsl/events.get.ts` (proxies `/api/dsl/events` with optional `offset`, `limit`, `type`, `aggregateType`, `aggregateId`, `correlationId`, `since`).

## Webhooks

The Webhooks page lists delivery outcomes for run-completion webhook subscriptions, newest first. The page is read-only: it surfaces the backend's own delivery log so operators can see which sinks succeeded and which retried / failed.

### User story

> As an operator, I want to see which run-completion webhook subscriptions actually delivered and which retried or failed, with the last error message, so I can diagnose a missed downstream ping without SSHing into the backend.

### What the page shows

The page wraps the `WebhookDeliveriesPanel` from `@cbs/components` (`frontend/components/src/components/WebhookDeliveriesPanel.vue`) inside a single white card. Page-level test id: `webhooks-page`.

Header:

- **"Webhooks"** title with subtitle "Delivery outcomes for run-completion webhook subscriptions, newest first."

Panel filter row:

| Filter | Test id |
|--------|----------|
| Subscription ID (text) | `webhook-deliveries-filter-subscription` |
| Apply | `webhook-deliveries-filter-apply` |
| Clear | `webhook-deliveries-filter-clear` |
| Refresh | `webhook-deliveries-refresh` |

Table columns (`webhook-deliveries-table`):

| Column | Test id | Notes |
|--------|---------|-------|
| When | — | Relative time with absolute `title` on hover. |
| Subscription | `webhook-deliveries-subscription` | |
| URL | `webhook-deliveries-url` | Masked when long. |
| Status | `webhook-deliveries-status` | Badge: `bg-green-100 text-green-800` when status `=== 'delivered'` (case-insensitive); neutral otherwise. |
| Attempts | `webhook-deliveries-attempts` | |
| Last error | `webhook-deliveries-last-error` | Truncated cell with `title` on hover. |

Pager: `webhook-deliveries-pager` with `Prev` (`webhook-deliveries-pager-prev`) / `Next` (`webhook-deliveries-pager-next`) buttons; range text on `webhook-deliveries-range`.

### States

- **Loading** — `webhook-deliveries-loading`.
- **Error** — `webhook-deliveries-error` with the backend message.
- **Empty** — `webhook-deliveries-empty`: "No webhook deliveries recorded."

### BFF routes

Hand-written, not yet in the spec (backend `GET /api/dsl/webhooks/deliveries` is annotated on
`WebhookRouterConfiguration` but absent from `docs/openapi.json` — stale spec; regen tracked in T767):

- `GET /api/v1/dsl/webhooks/deliveries` (`frontend/admin-ui-plugin/server/api/v1/dsl/webhooks/deliveries.get.ts`) proxies `/api/dsl/webhooks/deliveries` with optional `offset`, `limit`, `subscriptionId`.

Delivery record shape and failure-mode reference are in [`../runbook.md`](../runbook.md#inspect-webhook-deliveries).

## Notifications

The Notifications page manages notification rules that match domain events and fan out to webhook / email / Slack / PagerDuty sinks, and shows the recent fire log so operators can see which rules actually fired. See [`../architecture-ui.md`](../architecture-ui.md#notifications-page).

### User story

> As an operator, I want to define rules that fan domain events out to my on-call channels (webhook / email / Slack / PagerDuty), toggle them on and off, fire a synthetic event to test a rule, and read a fire log so I can see which rules actually fired in production.

### What the page shows

The page renders the `DslNotificationRuleList` panel from `@cbs/components` (`frontend/components/src/components/dsl/NotificationRuleList.vue`). Page-level test id: `notifications-page`.

Header:

- **"Notifications"** title with subtitle "Rules that match domain events and fan out to webhook, email, Slack, or PagerDuty sinks."

Panel surfaces (the page forwards all data and re-emits all events):

- **Rules table** — per-row toggle (enable/disable), edit, delete, "Test rule".
- **Create / edit form** — fields: `Name`, `Event type` (select from the known list), `Definition pattern` (glob), `Status filter`, `Sink` (webhook / email / slack / pagerduty), `URL`, `Secret` (webhook only), `To` (email only), `Priority`, `Rate class`.
- **Fire log** — paginated table loaded on mount via `loadFireLog(0)` with `FIRE_LOG_PAGE_SIZE = 25`.
- **Test rule form** — synthetic-event payload sent to `POST /api/v1/dsl/notifications/test`.

Known event types surfaced in the rule editor and test form:

`RunStarted`, `RunCompleted`, `RunFailed`, `RunCancelled`, `RunStale`, `DraftSaved`, `DraftPublished`, `PieceNotified`.

Known sink types: `webhook`, `email`, `slack`, `pagerduty`.

### States

- **Loading rules** — panel-level loading row.
- **Loading fire log** — `fireLogLoading` flag; embedded within the panel.
- **Error** — combined rule-error and fire-log-error surfaces.
- **Testing** — `testing` flag disables the test submit button and shows the result inline.
- **Delete confirm** — per-row inline confirm (the panel owns the confirm state).

### BFF routes

Hand-written, spec-covered (backend notification paths are present in `docs/openapi.json`):

- `GET /api/v1/dsl/notifications/rules?offset=&limit=`
- `POST /api/v1/dsl/notifications/rules`
- `GET /api/v1/dsl/notifications/rules/{id}`
- `PUT /api/v1/dsl/notifications/rules/{id}`
- `DELETE /api/v1/dsl/notifications/rules/{id}`
- `POST /api/v1/dsl/notifications/rules/{id}/enabled` (body `{ enabled: boolean }`)
- `GET /api/v1/dsl/notifications/channels` (sink catalog for the editor dropdown)
- `GET /api/v1/dsl/notifications/fire-log?ruleId=&offset=&limit=`
- `POST /api/v1/dsl/notifications/test`

## Approvals

The Approvals page is the operator inbox for change requests submitted from the DSL Workbench. Approving a request publishes the snapshot the requester captured; rejecting records the reason and never publishes. The page exists only when `cbs.dsl.approval.required=true` — otherwise there is nothing to approve. See [`../architecture-ui.md`](../architecture-ui.md#approvals-page-t568).

### User story

> As an approver, I want to see every pending change request the team has submitted, approve the ones that should ship and reject the ones that shouldn't (with a required reason), and read the comment trail on already-decided requests — without leaving the admin UI.

### What the page shows

The page (`frontend/admin-ui-plugin/app/pages/approvals.vue`) is a custom inline table — there is no shared panel yet.

Header:

- **"Approvals"** title with subtitle "Change requests submitted from the DSL workbench. Approving a request publishes the draft."
- **Status filter** dropdown (`approvals-status-filter`) — options: `All` / `Pending` / `Approved` / `Rejected` / `Superseded`.

Body states:

- **Loading** — `approvals-loading`: "Loading change requests…"
- **Error** — `approvals-error` banner (red) with the backend message.
- **Empty** — `approvals-empty`: "No change requests[ with status <filter>]."
- **Table** — `approvals-table` with one row per request (`approvals-row`).

Columns:

| Column | Test id | Notes |
|--------|---------|-------|
| Definition | `approvals-definition` | `request.definitionName`. |
| Requested by | — | `request.requestedBy`. |
| Requested at | — | Localised via `toLocaleString`; raw `requestedAt` if invalid. |
| Status | `approvals-status` | Badge colour: PENDING warning, APPROVED success, REJECTED error, SUPERSEDED neutral. |
| Actions | — | See below. |

Sort order: `PENDING` rows float to the top; ties break by newest `requestedAt`.

Actions (only when `request.status === 'PENDING'`):

- **Approve** button (`approvals-approve`, success colour) — gated by `guardAllowed('workbench-approve')` from `useManifestGuard`.
- **Reject** button (`approvals-reject`, error outline) — same gate; first click arms the row.
- A second inline row appears under the request showing the **Reason** input (`approvals-reject-reason`), **Confirm rejection** (`approvals-reject-confirm`, disabled until reason is non-blank), and **Cancel** (`approvals-reject-cancel`).
- For non-pending rows, the action cell renders the recorded `comment` as a small grey note instead of buttons.

### States

- **Loading / empty / error** — as listed.
- **Armed reject** — inline confirm row visible; other rows remain interactive.
- **Denied by guard** — Approve / Reject buttons render disabled (defence-in-depth UX; the backend re-evaluates the guard at execution time).

### RBAC / role gating

`useManifestGuard('workbench-approve')` from `frontend/admin-ui-plugin/app/composables/useManifestGuard.ts` (T551). The composable is defence-in-depth UX sugar; the security boundary is the server-side `PieceGuardFilter` re-evaluation at execution time. Manifest button-guard details live in [`../architecture-ui.md`](../architecture-ui.md#manifest-button-guard-t551).

### BFF routes

Mixed: the list endpoint is hand-written, the rest are OpenAPI-generated.

- `GET /api/v1/dsl/change-requests?definitionName=&status=` (`server/api/v1/dsl/change-requests/index.get.ts`).
- `POST /api/v1/dsl/drafts/{name}/change-request` — `submitChangeRequest` (snapshot the current draft).
- `POST /api/v1/dsl/change-requests/{id}/approve` — `approveChangeRequest` (optional `{ comment }` body).
- `POST /api/v1/dsl/change-requests/{id}/reject` — `rejectChangeRequest` (`comment` required by the backend).

Backend semantics, audit trail and direct-publish behaviour under the gate are in [`../runbook.md`](../runbook.md#publish-via-the-approval-gate).

## Promote

The Promote page moves published definition bundles between environments — preview the diff, then apply. It is the operator UI over the backend's `cbs.dsl.promotion.environments.<name>.base-path` config. See [`../architecture-ui.md`](../architecture-ui.md#promote-page-t569).

### User story

> As a release engineer, I want to select a source environment and a target environment, pick which definitions to move (or take them all), preview the created/updated/unchanged diff, and only then apply — so promotions stay reviewable and reversible.

### What the page shows

The page (`frontend/admin-ui-plugin/app/pages/promote.vue`) is a three-step flow inside one container (`promote-page` test id):

- **"Promote"** title with subtitle "Move published definition bundles between environments — preview the diff, then apply."

Step 1 — Environment pickers + "Include drafts":

| Control | Label | Test id | Notes |
|---------|-------|---------|-------|
| Source select | "Source environment" | `promote-source-select` | "Select source" placeholder option. |
| Target select | "Target environment" | `promote-target-select` | Source excluded via `selectableTargets`. |
| Include drafts | "Include drafts" | `promote-include-drafts` | Checkbox; toggling resets preview/apply state. |

Source states: `environmentsLoading` ("Loading environments…"), `environmentsError` (`promote-environments-error`), or `promote-no-environments` ("No promotion environments configured. Set `cbs.dsl.promotion.environments.<name>.base-path` on the backend.").

Step 2 (visible once `source` is set) — Definition picker (`grid-cols-1 lg:grid-cols-2`):

- Left card "Definitions in <source>" with a "Select all / Clear all" toggle (`promote-toggle-all`) and a checkbox per definition (`promote-def-{name}`). Each row shows `name` plus `type · status`. Footer: "All definitions will be promoted." or "<n> selected."
- Right card "Preview" with a "Preview diff" / "Previewing…" button (`promote-preview-button`).
- After a successful preview, the right card shows `promote-preview-summary` ("<N> of <total> definitions would change on <target>."), a `DslPromotionDiffTable` (`results` with `created` / `updated` / `unchanged` outcomes), and a "Promote to <target>" / "Promoting…" button (`promote-apply-button`) enabled when at least one row has `created` or `updated`.
- Placeholder before preview: `promote-preview-placeholder`: "Pick a source and target, then preview the diff before applying."

Step 3 — Apply result (`promote-apply-result`):

- Success banner: "Promoted <published> definition(s) to <target>."
- Failure tail: "<failed> definition(s) failed." when `applyResult.failed > 0`.
- A second `DslPromotionDiffTable` showing the apply results.
- A "View promotion audit log" link (`promote-audit-link`) pointing at `GET /api/v1/dsl/audit?action=PROMOTION`.

Apply errors render in `promote-apply-error`. Preview errors render in `promote-preview-error`.

### States

- **No environments** — `promote-no-environments` banner; nothing else renders.
- **Source set, target not chosen** — only the left "Definitions" card renders; preview is disabled.
- **Previewing** — preview button shows "Previewing…" and is disabled; apply button stays disabled.
- **Apply in flight** — apply button shows "Promoting…" and is disabled.
- **Apply error** — `promote-apply-error` text in red under the result block.

### BFF routes

Hand-written, spec-covered (backend promotion paths are present in `docs/openapi.json`):

- `GET /api/v1/dsl/promote/environments` — promotion-target catalog.
- `GET /api/v1/dsl/promote/definitions?env=<name>` — published definitions visible in the source env.
- `POST /api/v1/dsl/promote?dryRun=true` — preview; body `{ source, target, definitions?, includeDrafts? }`, returns `PromoteResult` with a `results` array.
- `POST /api/v1/dsl/promote` — same handler, no `dryRun` query param (apply; returns `{ published, failed, results }`).
- The page also links to `GET /api/v1/dsl/audit?action=PROMOTION` for the post-apply audit deep-link.

Environment configuration, prerequisites, dry-run vs apply semantics, and audit trail verification are in [`../runbook.md`](../runbook.md#promote-a-definition-between-environments).

## Layout, navigation and cross-cutting surfaces

- Sidebar nav, top-bar widgets (DslDraftsWidget, NotificationsWidget), footer and breakpoint behaviour: [`./layout.md`](./layout.md).
- Workbench publishing entry point (submits change requests to Approvals): [`./dsl-workbench.md`](./dsl-workbench.md).
- Execution deep-link targets opened from Activity (`/executions/{aggregateId}`): [`./execution-details.md`](./execution-details.md).
- BFF/composable contract for all six pages: [`../architecture-ui.md`](../architecture-ui.md).
- Tailwind colour palette used for the status badges on every page above: [`../colors.md`](../colors.md).