# Agent Guide: cbs-nova Frontend

This document guides coding agents working on the Vue/Nuxt frontend for the Temporal DSL Orchestration Engine.
Keep it updated as the workspace evolves.
Primary architecture docs: `docs/architecture-ui.md`, `docs/colors.md`, and `docs/frontend/`.

---

## 1. Project Map & Architecture

The frontend is a **pnpm workspace** under `./frontend`.

```
frontend/
├── package.json              # workspace root scripts and shared tooling
├── pnpm-workspace.yaml       # members: [admin-ui, components]
├── AGENTS.md                 # this file
├── admin-ui/                 # Nuxt 3 admin application
│   ├── app/
│   │   ├── components/       # admin-ui-only Vue components
│   │   ├── composables/      # shared Vue composables
│   │   ├── layouts/          # Nuxt layouts (shell, auth, etc.)
│   │   ├── pages/            # file-based routes
│   │   └── stores/           # Pinia stores
│   ├── server/               # Nitro TypeScript backend-for-frontend (BFF)
│   │   ├── api/v1/           # proxy routes to Spring Boot
│   │   └── utils/            # JWT helpers, HTTP client, config
│   ├── assets/css/main.css   # Tailwind entry point
│   ├── nuxt.config.ts
│   ├── tailwind.config.ts    # imports the shared preset
│   └── .env.example
└── components/               # Vue 3 + Vite reusable component library
    ├── src/
    │   ├── components/       # exported SFCs
    │   ├── composables/
    │   ├── tailwind.config.ts # canonical color theme
    │   └── index.ts          # public exports
    ├── package.json
    └── vite.config.ts
```

### `admin-ui`

- Nuxt 3 application that serves the admin web interface.
- Browser code lives under `app/`: pages, components, composables, layouts, Pinia stores.
- Server code lives under `server/` and is the **Backend-For-Frontend (BFF)**.
  The BFF holds the JWT, talks to the Spring Boot API, and shapes responses for the browser.

### `components`

- Standalone Vue 3 + Vite library.
- Exports reusable SFCs, composables, and the canonical Tailwind theme.
- Single-source-of-truth for colors: `components/src/tailwind.config.ts`.

---

## 2. Core Rules & Constraints

### Browser ↔ Backend Communication

- **The browser never calls the Spring Boot API directly.**
- All API traffic goes through `admin-ui/server/api/v1/*` Nitro routes.
- The BFF manages JWT acquisition/refresh, forwards authenticated calls, and translates errors into a browser-friendly shape.
- Store secrets and tokens server-side only; the browser only holds an `admin-ui` session cookie.

### Styling

- Use Tailwind CSS everywhere.
- Import the shared theme via `@cbs/components/tailwind.config` from `admin-ui/tailwind.config.ts`.
- Reference the canonical palette in `docs/colors.md` and the Tailwind config in `components/src/tailwind.config.ts`.
- Prefer semantic color tokens: `bg-background`, `text-neutral-800`, `bg-primary-500`, `bg-error-100`, etc.
- Follow the responsive shell described in `docs/frontend/layout.md` (sidebar + top bar + scrollable main content).

### Vue / Nuxt / TypeScript Practices

- Use `<script setup lang="ts">` for all new SFCs.
- Use the Composition API and typed composables.
- Keep pages thin; move logic into composables or Pinia stores.
- Pinia stores belong in `app/stores/` and are auto-imported by `@pinia/nuxt`.
- Server routes are TypeScript files under `server/api/v1/` that export `defineEventHandler` handlers.
- Add new environment variables to `admin-ui/.env.example` and read them with `useRuntimeConfig()` in server code.

### Workspace Rules

- Add reusable components to `components/` first; import them into `admin-ui` via `@cbs/components`.
- Do not duplicate color values in `admin-ui`; extend the shared Tailwind preset.
- Keep package versions in sync across `admin-ui` and `components` where it matters (Vue, Tailwind, TypeScript).

---

## 3. CLI Commands (Run from `frontend/`)

```bash
pnpm install                 # install all workspace dependencies
pnpm dev                     # start admin-ui development server
pnpm build                   # build admin-ui for production
pnpm build:components        # build the reusable component library
```

You can also run package scripts directly:

```bash
pnpm --filter admin-ui dev
pnpm --filter admin-ui build
pnpm --filter components build
```

---

## 4. Key Context & Conventions

### Backend Surface

- The Spring Boot backend exposes DSL endpoints documented in `docs/architecture-backend.md`.
- The BFF should proxy and reshape these under `server/api/v1/`.
- Expected operational modes: `run`, `preview`, `explain`.

### UI Design Docs

- `docs/frontend/index.md` — overview of the admin UI design docs.
- `docs/frontend/layout.md` — responsive shell (sidebar, top bar, main area).
- `docs/frontend/dsl-workbench.md` — DSL authoring workbench.
- `docs/frontend/runner.md` — run/preview/explain controls.
- `docs/frontend/execution-details.md` — execution inspection view.

### Color System

- Primary terracotta (`#ba7660`), warm neutral, and muted semantic colors (success, warning, error, info).
- `components/src/tailwind.config.ts` is the source of truth.
- See `docs/colors.md` for full mapping and accessibility notes.

---

## 5. Agent Workflows

- **Adding a page**: create a Vue file in `admin-ui/app/pages/` and wire navigation in the sidebar layout. Keep layout details in `app/layouts/`.
- **Adding a shared UI component**: build it in `components/src/components/`, export from `components/src/index.ts`, then import via `@cbs/components` in `admin-ui`.
- **Adding a Spring Boot API consumer**: add a Nitro route under `admin-ui/server/api/v1/`, reuse helpers from `server/utils/`, and keep the browser agnostic of backend URLs/tokens.
- **Changing the color palette**: edit `components/src/tailwind.config.ts` and `docs/colors.md`; do not hard-code colors elsewhere.
- **Adding client state**: add a Pinia store in `admin-ui/app/stores/` and prefer store actions for async state.

---

## 6. Onboarding Reading List

1. `docs/architecture-ui.md` — full frontend/BFF architecture.
2. `docs/colors.md` — Tailwind color palette.
3. `docs/frontend/index.md` — UI design overview.
4. `docs/frontend/layout.md` — responsive shell layout.
5. `docs/frontend/dsl-workbench.md` — DSL authoring experience.
6. `docs/frontend/runner.md` — run/preview/explain UI.
7. `docs/frontend/execution-details.md` — execution inspection UI.
