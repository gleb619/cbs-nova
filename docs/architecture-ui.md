# Temporal DSL Orchestration Engine — UI Architecture

This document describes the frontend tier of the cbs-nova project. It sits alongside the backend architecture
documented in `architecture-backend.md` and uses the color system defined in `colors.md`.

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

## Styling

All UI styling is based on the brandbook in `docs/colors.md`.

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

- `architecture-backend.md` — describes the Java / Temporal orchestration backend the admin UI consumes.
- `colors.md` — defines the Tailwind color palette used by both `admin-ui-plugin` and `components`.

## See also

- `docs/architecture-backend.md` — backend architecture and implementation roadmap
- `docs/colors.md` — admin UI color system
