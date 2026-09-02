# Temporal DSL Orchestration Engine — UI Architecture

This document describes the frontend tier of the cbs-nova project. It sits alongside the backend architecture
documented in [`architecture-backend.md`](architecture-backend.md) and uses the color system defined in [`colors.md`](colors.md).
The rationale behind the BFF-inside-the-Nuxt-module design is recorded in [`adr/0003-bff-nitro-admin-ui-plugin.md`](adr/0003-bff-nitro-admin-ui-plugin.md).

## Purpose

The frontend provides an administrative web interface for the orchestration engine. It is a Vue/Nuxt-based layer that
communicates with the existing Spring Boot backend through a small TypeScript backend-for-frontend (BFF) embedded inside
the Nuxt application.

## Workspace layout

The frontend lives in `./frontend` and is managed as a pnpm workspace.

```
frontend/
├── package.json                  # workspace root scripts and shared devDependencies
├── pnpm-workspace.yaml           # members: [admin-ui-plugin, components]
├── admin-ui-plugin/              # Nuxt module — the full admin UI as an installable plugin
│   ├── module.ts                 # Nuxt module entrypoint (defineNuxtModule)
│   ├── nuxt.config.dev.ts        # Standalone dev config — loads module.ts for local development
│   ├── app/
│   │   ├── components/           # plugin-specific Vue components
│   │   ├── composables/          # shared Vue composables (auto-imported by the module)
│   │   ├── layouts/              # Nuxt layouts registered by the module
│   │   ├── pages/                # pages registered by the module via extendPages
│   │   └── stores/               # Pinia stores
│   ├── server/                   # Nitro TypeScript backend (BFF)
│   │   ├── api/v1/               # proxy routes to Spring Boot
│   │   └── utils/                # JWT helpers, HTTP client, config
│   ├── assets/css/main.css       # global Tailwind stylesheet injected by the module
│   ├── tailwind.config.ts        # local Tailwind config (extends @cbs/components preset)
│   ├── package.json
│   └── .env.example
└── components/                   # reusable Vue component library
    ├── src/
    │   ├── components/           # exported SFCs
    │   ├── composables/
    │   ├── tailwind.config.ts    # canonical color theme
    │   └── index.ts              # public exports
    ├── package.json
    └── vite.config.ts
```

## Packages

### `@cbs/admin-ui-plugin`

A **Nuxt module** that installs the full CBS Nova admin UI into any host Nuxt application.

The module is the primary entry point (`module.ts`). When activated it:

- registers pages (Dashboard, Runner, DSL Workbench, Executions) via `extendPages`,
- registers the default shell layout (sidebar + top bar) via `addLayout`,
- adds `@pinia/nuxt` if not already present,
- injects the global Tailwind stylesheet,
- exposes composables to the host app via auto-import dirs,
- merges the Nitro `server/` directory into the host so BFF routes are available at `/api/v1/**`,
- merges `backendBaseUrl`, `backendApiKey`, and `appName` into the host runtime config.

Usage in a host `nuxt.config.ts`:

```ts
export default defineNuxtConfig({
  modules: ['@cbs/admin-ui-plugin'],
  adminUiPlugin: {
    routePrefix: '/admin',      // optional — defaults to '/'
    appName: 'My CBS Admin',    // optional
  },
})
```

For **standalone local development** the package ships `nuxt.config.dev.ts`, which loads the module
directly so the plugin can be developed and tested without a separate host app:

```bash
pnpm --filter @cbs/admin-ui-plugin dev
```

The Nuxt `server/` directory is the TypeScript backend of the admin UI. It is responsible for request
forwarding, response shaping, and error translation.

### `components` (`@cbs/components`)

A standalone Vue 3 + Vite library package. It exposes reusable components, composables, and the Tailwind
color theme so that other projects can embed them without depending on the full `admin-ui-plugin`.

## Communication with the backend

```
┌──────────────┐          ┌──────────────────────────────────┐          ┌──────────────┐
│   Browser    │  HTTP    │   Host Nuxt app                  │  HTTP +  │  Spring Boot │
│  (Vue pages) │ ───────▶ │  ┌──────────────────────────┐   │  JWT     │   API        │
│              │          │  │  @cbs/admin-ui-plugin     │   │────────▶│              │
└──────────────┘          │  │  Nitro server/ (BFF)      │   │          └──────────────┘
                          │  └──────────────────────────┘   │
                          └──────────────────────────────────┘
```

- The browser never talks directly to the Spring Boot API.
- Nuxt server routes request or refresh a JWT and forward authenticated calls to Spring Boot.
- The JWT is kept on the server side; the browser only holds its own session cookie.

### BFF security posture

The BFF is the only piece of the admin UI that talks to Spring Boot. Its security behaviour is governed by `frontend/admin-ui-plugin/server/utils/httpClient.ts` and `frontend/admin-ui-plugin/server/utils/oidcSession.ts`.

- **Header pass-through allowlist** — `proxyToBackend` forwards a small set of inbound headers to the backend: `Authorization`, `X-Api-Key`, `X-Request-Id`, `traceparent`, `Idempotency-Key`, and `X-Correlation-Id`. `X-Request-Id` is generated if absent. A static `X-Api-Key` is also sent when `backendApiKey` is configured.
- **No generic catch-all** — BFF routes are explicit Nitro files under `frontend/admin-ui-plugin/server/api/v1/`; the convention is recorded in [`../CLAUDE.md`](../CLAUDE.md). Each new backend path needs a matching proxy route.
- **Opt-in OIDC login flow** — When `AUTH_ISSUER` is unset, `/api/v1/auth/login`, `/api/v1/auth/callback`, and `/api/v1/auth/logout` return 404; `/api/v1/auth/session` returns `{ authenticated: false, enabled: false }`. When an issuer is configured, the module exposes four GET routes:
  - `/api/v1/auth/login` — builds PKCE + state, sets the short-lived `cbs_oidc_txn` httpOnly cookie, and redirects to the issuer.
  - `/api/v1/auth/callback` — validates state, exchanges the code, writes `cbs_at` + `cbs_rt` httpOnly cookies, and redirects back.
  - `/api/v1/auth/logout` — clears cookies and calls the issuer end-session endpoint.
  - `/api/v1/auth/session` — returns the OIDC userinfo session, refreshing once on 401/403.

  Session cookies are httpOnly, `SameSite=Lax`, and `Secure` only when the callback URL is HTTPS. The BFF attaches the session access token as `Authorization: Bearer <cbs_at>` unless the inbound request already provided an `Authorization` header (inbound wins). On a backend 401/403, the BFF attempts one token refresh using `cbs_rt` and retries the original request.

See [`architecture-backend.md`](architecture-backend.md#security) for the backend security layer (API key, rate limiting, OIDC resource-server) that the BFF proxies into.

## Runner panel components (`T153`, `T154`, `T159`, `T161`, `T162`, `T166`)

The primary interactive surface for running, previewing, and explaining DSL definitions lives in
`frontend/components/src/components/runner/`. Components are pure props-driven leaves and containers; the surrounding
page wires them to the BFF.

### Definition selector (`T182`)

`DefinitionSelector` (built on the BFF route `/api/dsl/definitions` fixed in `T182`) lists all registered DSL
entities with name and type. It is the runner's primary definition picker. The backend endpoint is `GET /api/dsl/definitions`
(`controllers.DslIntrospectionResource.definitions()`), returning `{name, type, inputSchema?}`.

### Input form and mode switcher

- `InputForm` / `InputField` render the selected definition's input schema and produce a JSON payload for the
  backend.
- `ModeSwitcher` lets the user choose `run`, `preview`, or `explain` and drives the active styling.
- `RunConfirmationModal` confirms destructive real runs.

### Output panels

`OutputPanel` is the container that switches between tabs based on the current mode and result shape:

- `ResultTab` — rendered JSON output.
- `MetadataTab` — execution metadata.
- `ErrorsTab` — structured errors from `PreviewErrorDetail` / `ErrorResponse`.

### Preview/Explain specific tabs

When the mode is `preview` or `explain`, the output panel exposes extra tabs:

- **Call Tree (`T153`)** — `CallTreeTab` renders the recursive `CallNode` AST produced by the backend. The root is
  passed as `tree`; `CallTreeNode` handles depth and expansion. The backend emits a `<truncated>` sentinel when the depth
  limit is exceeded.
- **External Calls (`T159`)** — `ExternalCallsTab` flattens every `externalCalls` entry under the call tree, grouped by
  source node. It color-codes normalized call types (`database`, `http`, `mq`, `filesystem`, `external_api`,
  `microservice`, `activity`, `other`) and formats timestamps.
- **Dry-Run Logs (`T154`)** — `DryRunLogsTab` renders the typed `dryRunLogs` array. Each row shows timestamp, level
  (color-coded), logger, and message. A "Copy all" button copies the plain-text dump to the clipboard.

The former **What-If Config** tab (`T161`, `WhatIfConfigPanel`) was removed in `T202`: `T198` deleted per-request
mocking from the backend entirely (`DslRuntimeResource.DslRequest` no longer has a `mocks` field), so the panel had
nothing left to configure. Faking external dependencies is now an operator-only concern, done via startup `cbs.nova.fakes`
YAML on the backend — see [`architecture-backend.md`](architecture-backend.md#fake-configuration-t198). There is no
runner-UI equivalent.

### Diff views

- **Explain diff (`T156`)** — `ExplainDiffView` compares the structured `explain` output with a previous `run` result.
  It offers a split view (explain vs. run) or a unified line-level diff. The unified diff is delegated to the shared
  `useDiffLines` composable, which builds an LCS table over the two JSON-serialized outputs.
- **Preview diff (`T166`)** — `PreviewDiffView` compares two preview results (baseline and current) across four tabs:
  - **Output Diff** — side-by-side JSON plus a unified LCS diff line view (`DiffLine`).
  - **AST Diff** — `ASTDiffNode` compares the two `CallNode` trees, marking nodes as `same`, `added`, `removed`, or
    `modified` and surfacing `propertyChanges`.
  - **External Calls Diff** — flattened call lists compared by `sourcePath|target|operation`, showing `added`/`removed`
    rows and reusing the type color-coding from `ExternalCallsTab`.
  - **Metrics Diff** — `MetricsDiffTable` compares `PreviewMetricsSnapshot` fields (execution duration, memory delta,
    call counts, external call counts). Lower-is-better metrics (latency, memory, counts) are colored green on decrease and
    red on increase.

The `usePreviewDiff` composable (the backing logic for `PreviewDiffView`) delegates the output-text LCS to
`useDiffLines` so the diff algorithm is not duplicated.

## Helper search (`T177`)

`frontend/components/src/composables/useHelperSearch.ts` provides a reusable search helper that wraps the backend
`/api/dsl/objects/search` endpoint (or the BFF proxy). It supports:

- debounced keyword search (`name`, `type`, `description`).
- `execute()` / `search()` / `clearFilters()` operations.
- loading, error, and empty states.
- a `hasActiveFilters` computed flag.

The backend endpoint searches across processes, transactions, helpers, and functions and returns
`{name, type, description, inputType, outputType}`.

## Introspection surface (`T182`)

Two complementary endpoints power the runner's discovery UI:

- `GET /api/dsl/definitions` — flat list of all registered entities with type and optional input schema (used by the
  definition picker).
- `GET /api/dsl/objects/search` — filterable search across the same entity set (used by the helper-search panel and
  any global search bar).

Both are wired through the BFF so the browser never calls Spring Boot directly.

## Executions page (`T186`/`T199`/`T200`)

The Executions page lists past and in-flight DSL runs. Before `T200` it had no working backend — the page was dead.

- **Backend (`T200`/`T217`)** — `controllers.DslExecutionsResource` exposes `GET /api/executions`
  (filterable by `processName`/`status`, `offset`/`limit` paginated list) and `GET /api/executions/{id}`
  (single-run detail), both reading through the existing `DslRunRepository`. Both are wired through the BFF like
  every other backend call.
- **Pagination (`T217`/`T218`)** — the list endpoint returns `{items, total}`; the page
  (`frontend/admin-ui-plugin/app/pages/executions/index.vue`) renders prev/next buttons and a page counter.
  The composable `useExecutions` in `frontend/admin-ui-plugin/app/composables/useExecutions.ts` calculates
  the `offset` from the current page and `pageSize`, calls `useExecutionsApi.list({ ...filters, offset, limit })`,
  and updates the list and `total`. `useExecutionsApi` forwards the query to `GET /api/v1/executions`.
- **STALE status (`T186`)** — the frontend `ExecutionStatus` type and status badge gained a `Stale` state, matching
  the backend `STALE` value set by the async process service's healthcheck (see
  [`architecture-backend.md`](architecture-backend.md) § "STALE status").
- **Auto-polling (`T199`)** — `frontend/admin-ui-plugin/app/composables/useStalePolling.ts` polls
  `GET /api/v1/executions/{id}` while a watched run is `Stale`, at a configurable interval
  (`public.stalePollMs`, default `5000`ms). Polling pauses while the browser tab is hidden (Page Visibility API) and
  fires one immediate re-check when the tab becomes visible again. As soon as a poll observes any status other than
  `Stale`, the composable pushes the new status into the caller's ref and stops — no further polling for that run.

## Workbench draft autosave (`T201`)

The DSL Workbench edits construct definitions (JSON/YAML) but previously lost all in-progress edits on a browser
refresh. `frontend/admin-ui-plugin/app/composables/useWorkbenchDraft.ts` adds client-only autosave:

- Body changes are debounced 250ms and written to `localStorage` under a per-construct key.
- Drafts expire after a 24h TTL — read attempts on an expired draft discard it and behave as if none existed.
- Scope is deliberately single-tab: there is no `storage` event listener, so concurrent tabs editing the same
  construct do not sync with each other.
- On restore, `restoredFromDraft` flips true so the caller can show a restore banner; `clearDraft()` discards the
  draft and resets the editor.
- SSR-safe: every `window`/`localStorage` access is guarded, so calling the composable during SSR is a no-op.

## Styling

All UI styling is based on the brandbook in [`colors.md`](colors.md).

- `components/src/tailwind.config.ts` is the canonical implementation of the palette.
- `admin-ui-plugin` imports the Tailwind preset from `@cbs/components` so the theme stays single-sourced.
- The module injects `assets/css/main.css` (Tailwind base + body tokens) into the host app automatically.

## Build and run

- Install dependencies: `pnpm install`
- Develop the admin UI (standalone): `pnpm --filter @cbs/admin-ui-plugin dev`
- Build the admin UI (standalone): `pnpm --filter @cbs/admin-ui-plugin build`
- Build the component library: `pnpm --filter components build`

Workspace shortcuts from `frontend/`:

```bash
pnpm dev                # start admin-ui-plugin standalone dev server
pnpm build              # build admin-ui-plugin standalone
pnpm build:components   # build component library
```

## Operator portal host app (`app/ui`)

`app/ui` is the **reference host Nuxt application** that consumes `@cbs/admin-ui-plugin` — it
stands in for what a real customer's admin portal looks like. Its `package.json` names it
`@cbs/operator-portal`. It does not duplicate any of the plugin's internals; it only wires
them into a host shell.

### What it demonstrates

- A client Nuxt 3 app with its own landing page and shell layout.
- The CBS Nova admin UI mounted under `/nova-admin` via the plugin module — same `defineNuxtConfig`
  + `modules: ['@cbs/admin-ui-plugin']` pattern documented above, with `routePrefix: '/nova-admin'`.
- The BFF routes merged automatically from the plugin (the plugin's `server/` directory is merged
  into the host's Nitro server, so `/api/v1/**` is served from the host process and the browser
  never calls Spring Boot directly).
- Local package distribution via `pnpm pack` (the npm equivalent of `mavenLocal`): the host
  resolves the plugin from a local tarball, not a remote registry.

### Local-registry flow

The host does not depend on a remote npm registry. Instead, `app/ui/local-registry/` holds
freshly packed tarballs of `frontend/components` and `frontend/admin-ui-plugin`. The
`pack:local` script in `app/ui/package.json` rebuilds the tarballs and rewrites the host
`package.json` to point at the new versions:

```bash
cd app/ui
pnpm install
pnpm pack:local          # pack frontend/components + frontend/admin-ui-plugin into local-registry/
pnpm install             # reinstall against the new tarballs
pnpm dev                 # host Nuxt + mounted /nova-admin at http://localhost:3000
```

This is the same operational pattern a customer would use to integrate CBS Nova into their
own admin portal: produce a package, point the host at it, mount the module.

### Smoke test

`app/ui/tests/` contains a minimal Vitest smoke test that mounts the host landing page and
asserts the portal branding and the admin entry link are rendered. Run it with `pnpm test`
from `app/ui`. It is the counterpart to the backend's `ComposeStackValidationTest` — each
layer of the deployment topology has at least one wire-it-up test that proves the compose /
host wiring actually starts.

### Architecture notes

- The host app uses **explicit Vue/Nuxt imports** for plugin pages, composables, and the shared
  component library so they resolve reliably when consumed from `node_modules`.
- The plugin exposes its composables via package subpath exports (`@cbs/admin-ui-plugin/composables/*`)
  so host apps and plugin pages can import them without brittle relative paths.
- The plugin module registers `@cbs/components` with `pathPrefix: false` so the library's
  internal SFC references resolve correctly when mounted from the host.

The compose-side relationship (browser → host → BFF → Spring Boot `app/server`) is identical
to the diagram in the "Communication with the backend" section above; the operator portal
adds the host shell around the plugin and the local-registry tarball flow around the dependency.
See [`architecture-backend.md`](architecture-backend.md#app-deployment-topology) for the
shared Postgres + per-domain DB topology and the full compose diagram.

## Frontend design details

Detailed UI design documentation lives in `docs/frontend/`:

- [`frontend/index.md`](./frontend/index.md) — overview and entry point for the admin UI design docs.
- [`frontend/layout.md`](./frontend/layout.md) — overall page layout, navigation, and responsive shell.
- [`frontend/dsl-workbench.md`](./frontend/dsl-workbench.md) — DSL editing experience: editor, validation, and flow structure.
- [`frontend/runner.md`](./frontend/runner.md) — run/preview controls, input forms, and mode selection.
- [`frontend/execution-details.md`](./frontend/execution-details.md) — execution view, event history, status, and diagnostics.

## Relationship to other docs

- [`architecture-backend.md`](architecture-backend.md) — describes the Java / Temporal orchestration backend the admin UI consumes.
- [`colors.md`](colors.md) — defines the Tailwind color palette used by both `admin-ui-plugin` and `components`.

## See also

- [`architecture-backend.md`](architecture-backend.md) — backend architecture and implementation roadmap
- [`colors.md`](colors.md) — admin UI color system
