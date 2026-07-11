# Agent Guide: cbs-nova Frontend

Coding agent reference for Vue/Nuxt frontend. Architecture docs: `docs/architecture-ui.md`, `docs/colors.md`, `docs/frontend/`.

## Structure

```
frontend/
├── admin-ui/                 # Nuxt 3 app (pages, BFF, auth)
│   ├── app/
│   │   ├── components/       # ONLY app-specific shell/auth wrappers
│   │   ├── pages/            # file-based routes
│   │   ├── stores/           # Pinia stores
│   │   └── layouts/          # Nuxt layouts
│   └── server/               # Nitro BFF: JWT, proxy to Spring Boot
└── components/               # reusable Vue 3 + Vite library
    └── src/
        ├── components/       # exported SFCs
        ├── composables/      # reusable composables
        ├── types/            # shared domain types
        └── tailwind.config.ts # canonical theme
```

**Rule:** Most code lives in `components/`. `admin-ui` is thin—imports bulk of UI from `@cbs/components`.

## Core Rules

### Components-first
- **Reusable components/composables/types go in `components/src/` first**
- `admin-ui` imports via `@cbs/components` or `@cbs/components/types`
- No duplication between `components/` and `admin-ui/app/`

### What stays in `admin-ui`
- Pages, layouts, Pinia stores
- BFF routes (`server/api/v1/`) and JWT helpers (`server/utils/`)
- BFF-coupled composables (e.g., `useDslApi`)
- App-specific Nuxt wrappers

### What moves to `components`
- All reusable SFCs
- Generic UI composables (e.g., `useSidebar`)
- Domain types (`dsl.ts`, `execution.ts`, `runner.ts`)
- Pure helpers/formatters

### Communication
- **Browser never calls Spring Boot directly**
- All API traffic through `admin-ui/server/api/v1/*` Nitro routes
- BFF manages JWT, forwards calls, translates errors
- Browser holds only session cookie; tokens server-side

### Styling
- Use Tailwind CSS everywhere
- Import shared theme via `@cbs/components/tailwind.config`
- Source of truth: `components/src/tailwind.config.ts`
- Prefer semantic tokens: `bg-background`, `text-neutral-800`, `bg-primary-500`
- No hard-coded colors in `admin-ui`

### Vue/Nuxt/TS
- Use `<script setup lang="ts">` for all SFCs
- Composition API + typed composables
- Keep pages thin; logic in composables/stores
- Pinia stores in `app/stores/` (auto-imported)
- Server routes: TypeScript files under `server/api/v1/` exporting `defineEventHandler`

### `components` purity
- No Nuxt-only imports in `components/src/` (no `NuxtLink`, `useRouter`, `$fetch`, `useState`)
- Use standard Vue APIs (`RouterLink`, `Teleport`, `Transition`)
- Accept routing via props/slots
- `vue` and `vue-router` as peer/dev dependencies

## CLI Commands (run from `frontend/`)

```bash
pnpm install                 # install workspace dependencies
pnpm dev                     # start admin-ui dev server
pnpm build                   # build admin-ui
pnpm build:components        # build component library
pnpm test                    # run admin-ui tests
pnpm check                   # biome lint + format check
pnpm check:fix               # biome fix
```

Package-specific:
```bash
pnpm --filter admin-ui dev
pnpm --filter components build
```

## Agent Workflows

- **Add page**: Create Vue file in `admin-ui/app/pages/`, wire navigation in sidebar. Import from `@cbs/components`.
- **Add shared component**: Build in `components/src/components/`, export from `components/src/index.ts`, import via `@cbs/components` in `admin-ui`.
- **Add API consumer**: Add Nitro route under `admin-ui/server/api/v1/`, reuse `server/utils/` helpers.
- **Change colors**: Edit `components/src/tailwind.config.ts` and `docs/colors.md`.
- **Add client state**: Add Pinia store in `admin-ui/app/stores/`.
- **Move code to components**: Update internal imports to relative paths, update `components/src/index.ts` exports, adjust `admin-ui` imports.

## Key Docs

- `docs/architecture-ui.md` — full frontend/BFF architecture
- `docs/colors.md` — color palette
- `docs/frontend/index.md` — UI design overview
- `docs/frontend/layout.md` — responsive shell
- `docs/frontend/dsl-workbench.md` — DSL authoring
- `docs/frontend/runner.md` — run/preview/explain UI
- `docs/frontend/execution-details.md` — execution inspection
