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
├── admin-ui/                 # thin Nuxt 3 admin application
│   ├── app/
│   │   ├── components/       # ONLY app-specific shell / auth wrappers
│   │   ├── composables/      # BFF-coupled or admin-specific composables
│   │   ├── layouts/          # Nuxt layouts (shell)
│   │   ├── pages/            # file-based routes
│   │   ├── plugins/          # global registration of @cbs/components
│   │   └── stores/           # Pinia stores
│   ├── server/               # Nitro BFF: JWT, proxy to Spring Boot
│   ├── assets/css/main.css   # Tailwind entry point
│   ├── nuxt.config.ts
│   ├── tailwind.config.ts    # imports shared preset
│   └── .env.example
└── components/               # reusable Vue 3 + Vite plugin module
    ├── src/
    │   ├── components/       # exported SFCs
    │   ├── composables/      # reusable Vue composables
    │   ├── types/            # shared domain / UI types
    │   ├── tailwind.config.ts # canonical theme
    │   └── index.ts          # public exports
    ├── package.json
    └── vite.config.ts
```

### Design rule

**Most frontend code lives in `components/`. `admin-ui` is a thin Nuxt app that owns auth, the BFF, pages, layouts, and
stores, and imports the bulk of its UI components, composables, and types from `@cbs/components`.**

This lets other teams reuse CBS Nova UI pieces in their own apps without pulling in the whole admin application.

### `admin-ui`

- Nuxt 3 application that serves the admin web interface.
- Browser code under `app/` must stay thin. Pages bind data from the BFF to components that come from `@cbs/components`.
- `app/components/` should contain **only** app-specific shell wrappers or auth-gated chrome. The reusable versions of
  those components live in `components/src/components/`.
- Server code under `server/` is the **Backend-For-Frontend (BFF)**. It holds JWT state, talks to Spring Boot, and
  reshapes responses for the browser.

### `components`

- Standalone Vue 3 + Vite library. It must not depend on Nuxt-only APIs (`$fetch`, `useState`, `useRoute`, `NuxtLink`,
  etc.).
- Exports reusable SFCs, composables, domain types, and the canonical Tailwind theme.
- Single-source-of-truth for colors: `components/src/tailwind.config.ts`.

---

## 2. Core Rules & Constraints

### Components-first ownership

- **Every new reusable component, composable, or domain type must be created in `components/src/` first.** Only
  app-specific wiring may live in `admin-ui`.
- `admin-ui` imports them via `@cbs/components` (SFCs and composables) or `@cbs/components/types` (types).
- Do not duplicate components, types, or composables between `components/` and `admin-ui/app/`.

### What stays in `admin-ui`

- Pages (`app/pages/`), layouts (`app/layouts/`), Pinia stores (`app/stores/`).
- BFF server routes (`server/api/v1/`) and JWT/config helpers (`server/utils/`).
- Composables that are tightly coupled to BFF endpoints or Nuxt-only APIs (e.g., `useDslApi`, `useExecutionsApi`).
- App-specific wrappers that bind Nuxt concepts (e.g., passing `NuxtLink` into a generic nav component, wiring
  route-based state).

### What moves to `components`

- All reusable SFCs.
- Generic UI composables (e.g., `useSidebar`).
- Domain data types (`dsl.ts`, `execution.ts`, `runner.ts`).
- Pure helpers and formatters.

### Browser ↔ Backend Communication

- **The browser never calls the Spring Boot API directly.**
- All API traffic goes through `admin-ui/server/api/v1/*` Nitro routes.
- The BFF manages JWT acquisition/refresh, forwards authenticated calls, and translates errors into a browser-friendly
  shape.
- Store secrets and tokens server-side only; the browser only holds an `admin-ui` session cookie.

### Styling

- Use Tailwind CSS everywhere.
- Import the shared theme via `@cbs/components/tailwind.config` from `admin-ui/tailwind.config.ts`.
- Reference the canonical palette in `docs/colors.md` and the Tailwind config in `components/src/tailwind.config.ts`.
- Prefer semantic color tokens: `bg-background`, `text-neutral-800`, `bg-primary-500`, `bg-error-100`, etc.
- Follow the responsive shell described in `docs/frontend/layout.md` (sidebar + top bar + scrollable main content).
- Do not hard-code color values in `admin-ui`.

### Vue / Nuxt / TypeScript Practices

- Use `<script setup lang="ts">` for all new SFCs.
- Use the Composition API and typed composables.
- Keep pages thin; move logic into composables or Pinia stores.
- Pinia stores belong in `app/stores/` and are auto-imported by `@pinia/nuxt`.
- Server routes are TypeScript files under `server/api/v1/` that export `defineEventHandler` handlers.
- Add new environment variables to `admin-ui/.env.example` and read them with `useRuntimeConfig()` in server code.

### `components` library purity

- No Nuxt-only imports inside `components/src/` (no `NuxtLink`, `useRouter`, `$fetch`, `useState`, `definePageMeta`).
- Use standard Vue / `vue-router` APIs (`RouterLink`, `Teleport`, `Transition`).
- Accept navigation/routing behavior from the consuming app via props or slots.
- Keep `vue` and `vue-router` as peer/dev dependencies so both Nuxt and plain Vue apps can consume the library.

### Workspace Rules

- Add reusable components to `components/src/components/` first; import them into `admin-ui` via `@cbs/components`.
- Do not duplicate color values in `admin-ui`; extend the shared Tailwind preset.
- Keep package versions in sync across `admin-ui` and `components` where it matters (Vue, Tailwind, TypeScript).

---

## 3. CLI Commands (Run from `frontend/`)

```bash
pnpm install                 # install all workspace dependencies
pnpm dev                     # start admin-ui development server
pnpm build                   # build admin-ui for production
pnpm build:components        # build the reusable component library
pnpm test                    # run admin-ui tests
pnpm test:components         # run components library tests (after setup)
pnpm check                   # biome lint + format check across workspace
pnpm check:fix               # biome fix across workspace
```

You can also run package scripts directly:

```bash
pnpm --filter admin-ui dev
pnpm --filter admin-ui build
pnpm --filter admin-ui test
pnpm --filter components build
pnpm --filter components test
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

- **Adding a page**: create a Vue file in `admin-ui/app/pages/` and wire navigation in the sidebar layout. Keep layout
  details in `app/layouts/`. Import shared components from `@cbs/components`.
- **Adding a shared UI component**: build it in `components/src/components/`, export from `components/src/index.ts`,
  then import via `@cbs/components` in `admin-ui`. Never add it directly to `admin-ui/app/components/` unless it is pure
  app chrome.
- **Adding a Spring Boot API consumer**: add a Nitro route under `admin-ui/server/api/v1/`, reuse helpers from
  `server/utils/`, and keep the browser agnostic of backend URLs/tokens.
- **Changing the color palette**: edit `components/src/tailwind.config.ts` and `docs/colors.md`; do not hard-code colors
  elsewhere.
- **Adding client state**: add a Pinia store in `admin-ui/app/stores/` and prefer store actions for async state.
- **Moving code from `admin-ui` to `components`**: update all internal imports to relative paths inside `components/`,
  update `components/src/index.ts` exports, and adjust `admin-ui` consumers to import from `@cbs/components`.

---

## 6. Onboarding Reading List

1. `docs/architecture-ui.md` — full frontend/BFF architecture.
2. `docs/colors.md` — Tailwind color palette.
3. `docs/frontend/index.md` — UI design overview.
4. `docs/frontend/layout.md` — responsive shell layout.
5. `docs/frontend/dsl-workbench.md` — DSL authoring experience.
6. `docs/frontend/runner.md` — run/preview/explain UI.
7. `docs/frontend/execution-details.md` — execution inspection UI.
8. `frontend/components/src/index.ts` — current public API of the shared library.
