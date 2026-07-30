# Temporal DSL Orchestration Engine — UI Architecture

This document describes the frontend tier of the cbs-nova project. It sits alongside the backend architecture
documented in [`architecture-backend.md`](architecture-backend.md) and uses the color system defined in [`colors.md`](colors.md).

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

When the mode is `preview` or `explain`, the output panel exposes three extra tabs:

- **Call Tree (`T153`)** — `CallTreeTab` renders the recursive `CallNode` AST produced by the backend. The root is
  passed as `tree`; `CallTreeNode` handles depth and expansion. The backend emits a `<truncated>` sentinel when the depth
  limit is exceeded.
- **External Calls (`T159`)** — `ExternalCallsTab` flattens every `externalCalls` entry under the call tree, grouped by
  source node. It color-codes normalized call types (`database`, `http`, `mq`, `filesystem`, `external_api`,
  `microservice`, `activity`, `other`) and formats timestamps.
- **Dry-Run Logs (`T154`)** — `DryRunLogsTab` renders the typed `dryRunLogs` array. Each row shows timestamp, level
  (color-coded), logger, and message. A "Copy all" button copies the plain-text dump to the clipboard.
- **What-If Config (`T161`)** — `WhatIfConfigPanel` lets the user add mock entries keyed by
  `type:target:operation` with a JSON payload. The payload is sent to `/api/dsl/preview/{name}` as the `mocks` map. The
  panel validates that the payload is a JSON object and surfaces per-row errors. See the backend limitation in
  [`architecture-backend.md`](architecture-backend.md): Activity and MQ mocks are fully applied; DB and HTTP mocks are only
  recorded as metadata and the real call still executes.

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
`/api/dsl/helpers/search` endpoint (or the BFF proxy). It supports:

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
- `GET /api/dsl/helpers/search` — filterable search across the same entity set (used by the helper-search panel and
  any global search bar).

Both are wired through the BFF so the browser never calls Spring Boot directly.

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
