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
| `audit-write` | `action: <name>` | writes an audit record; action names align with `core/StarterConstants` `ACTION_*` constants |
| `invariant-assert` | `expr` and/or `description` | asserts a post-condition; semantics defined by T550 |
| `notify` | `channel: <name>` | emits a best-effort log/event to the named channel |

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

## `failMode` vs hook `onFailure`

`failMode` governs **pre-checks** only:

- `deny` — a failed pre-check rejects the request (default).
- `audit-only` — a failed pre-check is logged/audited, but the request is still allowed.

T550 will add a per-hook `onFailure` policy (`warn` \| `block-next-execution`) that governs
**post-check** hooks. These two knobs are not the same: `failMode` is about pre-check access
control, `onFailure` is about what happens when a post-execution hook fails.

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
