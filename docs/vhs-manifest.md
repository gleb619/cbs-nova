# VHS piece manifest

A single declarative contract describing a "piece" of the cbs-nova control plane: what it is,
who may trigger it, what must hold before it runs, and what must happen after it runs.

Epic 6 replaces the three manually maintained copies of the same idea
(`RbacAuthorizationFilter.RULES`, `RateLimitFilter`'s hardcoded route list, and ad-hoc audit
calls in handlers such as `DslReloadHandler.audit()` and `DslScheduleHandler`) with one manifest
that T548–T553 will load, enforce, and guard against drift.

This document ships the model and worked examples. Runtime behavior is intentionally out of
scope here.

## Lifecycle

```
preCheck[]  →  execute  →  postCheck[]
   │                │            │
   │                │            └─ audit-write / invariant-assert / notify
   │                └─ the actual button click, API call, or object invocation
   └─ role / feature-flag / rate-class
```

`preCheck` checks run in order; a single failure is governed by `failMode`.
`postCheck` hooks run only after successful execution.

## Top-level document

One YAML file with a single `pieces:` array. Each piece has:

| field | shape | meaning |
|-------|-------|---------|
| `id` | kebab-case string | stable identifier, unique within the manifest |
| `target` | `{ type: api \| button \| object, ... }` | what the piece guards |
| `preCheck` | ordered array | checks that must all pass before execution |
| `postCheck` | ordered array | hooks that run after successful execution |
| `failMode` | `deny` (default) \| `audit-only` | policy for pre-check failure |

## Target types

### `api`

Guards an HTTP route. Required sub-field: `route` (`METHOD path`, e.g. `POST /api/dsl/reload`).

### `button`

Guards a UI action. Required sub-field: `uiKey` (a component key / `data-testid` the frontend
will bind with T551's `useManifestGuard`).

### `object`

Allowlists or guards a DSL object. Required sub-fields: `objectType` (`helper`, `process`, or
`function`) and `objectName` (the object name). This supports the Epic 2 sandboxing idea:
certain helpers may be allowlisted only in preview mode or specific runtime contexts.

## Pre-check types

| type | fields | semantics |
|------|--------|-----------|
| `role` | `anyOf: [role-name, ...]` | caller must satisfy at least one of the named `cbs.nova.starter.security.Role` values (`viewer`, `runner`, `author`, `operator`, `admin`) |
| `feature-flag` | `flag: <name>` | flag must be enabled; the flag source is left to T549 |
| `rate-class` | `class: <name>` | assigns the piece to a named rate-limit class, decoupling pieces from `CbsSecurityRateLimitProperties` internals |

## Post-check (hook) types

| type | fields | semantics |
|------|--------|-----------|
| `audit-write` | `action: <name>`, `onFailure` | writes an audit record; action names align with `core/StarterConstants` `ACTION_*` constants |
| `invariant-assert` | `expr` and/or `description`, `onFailure` | asserts a post-condition over the execution snapshot (named conditions, see below) |
| `notify` | `channel: <name>`, `onFailure` | emits a best-effort log line + `PieceNotified` domain event to the named channel (real sinks are Epic 3) |

Every hook accepts `onFailure: warn (default) | block-next-execution` (see below).

## Enforcement semantics (T549)

`PieceGuardFilter` enforces the `preCheck[]` of `api`-target pieces on the live request path:

- **Opt-in by manifest presence.** The filter consults `PieceManifestService.findByRoute(method,
  path)` on every `/api/*` request. A route with no matching piece proceeds byte-for-byte
  untouched — no attributes, no response rewriting, no rate-bucket consumption. There is no
  global gate.
- **Check order.** Checks evaluate in manifest order; the first failure governs the response.
  - `role` — resolved via `RoleResolver` (X-Api-Key → ADMIN; JWT `roles` claim with
    `scope`/`scp` fallback; anonymous → VIEWER). The caller passes if its role
    `satisfies()` at least one of `anyOf`.
  - `feature-flag` — evaluated by the `FeatureFlagSource` seam. Default implementation is
    properties-backed: `cbs.dsl.manifest.flags.<name>=true`. Absent/false = disabled
    (fail-closed). A real flag platform can replace it with a bean.
  - `rate-class` — the guard owns a token bucket per (rate class, principal). Class shape:
    `cbs.dsl.manifest.rate-classes.<name>.capacity` / `.refill-per-second`. A class named by
    the manifest but not configured **fails closed** (403 + audit). These buckets are additive
    and independent of the global `cbs.security.ratelimit.*` filter, which keeps its own
    per-client-IP buckets and hardcoded route list — the guard does not double-limit.
- **Failure.** `deny` (default) → 403 with the unified `ErrorResponse` envelope
  (`code = "FORBIDDEN"`, message naming the check type and piece id, `context` carrying
  `pieceId` and `check`). `audit-only` → a FAILURE `dsl_audit` row (action
  `PIECE_GUARD_DENY`, when `DslAuditService` is present) and the request continues.
- **Success attributes.** The matched piece id (`cbs.nova.piece.id`) and resolved role
  (`cbs.nova.piece.principal-role`) are set as request attributes for the T550 post-check
  pipeline to reuse without re-resolving.
- **Filter ordering.** Registered at `Ordered.HIGHEST_PRECEDENCE + 3`, after the API-key (+1),
  RBAC and global rate-limit (+2) filters, before the route handler. The guard is additive to
  `RbacAuthorizationFilter`, which stays authoritative where enabled; migrating `RULES` into
  manifest pieces is a follow-up.
- **Activation.** First-class auto-configuration gated on `@ConditionalOnBean(PieceManifestService)`:
  when the manifest subsystem is off the filter is not registered at all.

## Post-check enforcement semantics (T550)

`PieceCheckPipeline` runs a piece's `postCheck[]` hooks after the guarded action completes
**successfully**:

- **Success-only trigger.** `PieceGuardFilter` invokes the pipeline only after the filter chain
  returns without exception and the response status is `< 400`. Handler errors, 4xx/5xx
  responses, and pre-check denials under `failMode: deny` trigger nothing. (Under
  `failMode: audit-only` the request is allowed, so a successful execution still triggers hooks.)
- **Off the request thread.** Hooks run on a dedicated named-thread pool
  (`cbs-piece-check-N`, size `cbs.dsl.manifest.post-check.executor-pool-size`, default 2) and
  never delay the response — the response is already sent by the time hooks run, and hooks can
  never change the outcome of the execution they observe.
- **`invariant-assert` expressions.** Deliberately conservative — `expr` is one of a small set
  of named conditions over the execution snapshot (`InvariantContext`: piece id, principal role,
  method, path, status), not an expression language:
  - `success` (default when `expr` is absent/blank) — status `< 400`
  - `status-2xx` — status in `[200, 300)`
  - `status-3xx` — status in `[300, 400)`

  A violated assertion — or an unrecognized `expr`, which is a failure rather than a pass — is
  governed by the hook's `onFailure` policy.
- **`audit-write`** reuses `dsl_audit` (action from the check, outcome `SUCCESS`; the actor is
  the principal resolved at request time). It is a no-op when no `DslAuditService` bean exists,
  and inherits the service's fail-safe warn+swallow behavior.
- **`notify`** is log/event only: an INFO log line plus a best-effort `PieceNotified` domain
  event on the existing `dsl_events` mechanism (same pattern as `DslReloadHandler`'s
  best-effort `ReloadFailed` publish). Real notification sinks (Slack/email/webhooks) are
  Epic 3.
- **Observability.** Micrometer counters `dsl.piece.postcheck.total` /
  `dsl.piece.postcheck.failed`, tagged `hook=<type>`; hook-thread logs carry the request's
  correlation id in the MDC `rid` key (T384).

### Hook failure policies

`onFailure` governs **post-check** hooks (and is not the same knob as `failMode`, which governs
pre-check access control):

- `warn` (default) — the failure is logged at ERROR with the correlation id and a FAILURE
  `dsl_audit` row (action `PIECE_POSTCHECK_FAILURE`) is written. Nothing else happens; the
  already-sent response is unaffected.
- `block-next-execution` — as `warn`, plus the piece is blocked from further execution:
  `PieceGuardFilter` consults the block registry **before** evaluating pre-checks and denies
  with `403`, unified envelope, `code = "PIECE_BLOCKED"`, `context = {pieceId, check:
  "post-check-block"}` while the block is active.

  Blocks live in an in-memory TTL registry
  (`cbs.dsl.manifest.post-check.block-ttl`, default 30m; `block-scope: piece` (default — the
  block is global for the piece) or `principal` (only the principal that tripped it)). They are
  visible in logs (WARN on raise and on every denied request) and clear three ways: TTL expiry,
  a **successful manifest reload** (`POST /api/dsl/manifest/reload` — the operator's
  "reviewed and fixed" signal clears all blocks), and restart.

  In-memory by design, consistent with the guard's in-memory rate-limit buckets: enforcement is
  single-instance, and a block on one replica does not deny traffic served by another.
  Multi-replica blocking needs a shared store (Epic 2 distributed follow-up).

### Rollback is out of scope

Rollback/compensation of the executed piece is explicitly **not** attempted: most executions
(Temporal workflow starts, published definitions) are not transactionally reversible, and DSL
compensation machinery is unrelated to guard hooks. `block-next-execution` is the strongest
available corrective, and it is deliberately manual-review-shaped — an operator inspects the
failure audit rows and logs, fixes the cause, then clears the block via manifest reload (or
waits out the TTL / restarts).

## Worked examples

### API piece: reload DSL definitions

```yaml
- id: dsl-reload
  target:
    type: api
    route: POST /api/dsl/reload
  preCheck:
    - type: role
      anyOf: [author, operator, admin]
    - type: rate-class
      class: control-plane
  postCheck:
    - type: audit-write
      action: DEFINITION_RELOAD
    - type: notify
      channel: workbench
  failMode: deny
```

### Button piece: publish from the workbench

```yaml
- id: workbench-publish
  target:
    type: button
    uiKey: workbench-publish-btn
  preCheck:
    - type: role
      anyOf: [author, admin]
    - type: feature-flag
      flag: workbench-publish
  postCheck:
    - type: notify
      channel: workbench
  failMode: deny
```

### Object piece: allowlist a preview-mode helper

```yaml
- id: preview-sandbox-helper
  target:
    type: object
    objectType: helper
    objectName: PreviewSandbox
  preCheck:
    - type: role
      anyOf: [runner, author, admin]
  postCheck:
    - type: invariant-assert
      description: preview helper returned only sandbox-safe types
  failMode: audit-only
```

## Schema

JSON Schema Draft 2020-12: [`docs/schema/piece-manifest.schema.json`](schema/piece-manifest.schema.json).

A starter instance lives at `app/dsl/src/main/resources/piece-manifest.yaml`.
