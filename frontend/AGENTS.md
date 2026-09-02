# Agent Guide: cbs-nova Frontend

Coding agent reference for Vue/Nuxt frontend. Architecture docs: `docs/architecture-ui.md`, `docs/colors.md`, `docs/frontend/`.

## Structure

```
frontend/
├── admin-ui-plugin/          # Nuxt module — mounts the full admin UI into any host Nuxt app
│   ├── module.ts             # Nuxt module entrypoint (defineNuxtModule)
│   ├── nuxt.config.dev.ts    # Standalone dev config — loads module.ts for local development
│   ├── app/
│   │   ├── components/       # ONLY plugin-specific shell/auth wrappers
│   │   ├── pages/            # file-based routes registered by the module
│   │   ├── stores/           # Pinia stores (auto-imported by the module)
│   │   └── layouts/          # Nuxt layouts registered by the module
│   └── server/               # Nitro BFF: JWT, proxy to Spring Boot
│       ├── api/v1/           # proxy routes merged into the host Nitro server
│       └── utils/            # config helpers, HTTP client
└── components/               # reusable Vue 3 + Vite library
    └── src/
        ├── components/       # exported SFCs
        ├── composables/      # reusable composables
        ├── types/            # shared domain types
        └── tailwind.config.ts # canonical theme
```

**Rule:** Most code lives in `components/`. `admin-ui-plugin` is thin — imports bulk of UI from `@cbs/components`.

## Core Rules

### Plugin architecture
- `admin-ui-plugin` is a **Nuxt module** (`module.ts`). A host Nuxt app activates it via:
  ```ts
  // host nuxt.config.ts
  export default defineNuxtConfig({
    modules: ['@cbs/admin-ui-plugin'],
  })
  ```
- `nuxt.config.dev.ts` is only used for standalone local development (`pnpm dev`).
- The module registers pages, layouts, composables, Nitro server routes, and runtime config into the host app automatically.
- `routePrefix` option controls where admin routes are mounted (default: `/`).

### Components-first
- **Reusable components/composables/types go in `components/src/` first**
- `admin-ui-plugin` imports via `@cbs/components` or `@cbs/components/types`
- No duplication between `components/` and `admin-ui-plugin/app/`

### What stays in `admin-ui-plugin`
- Pages, layouts, Pinia stores
- BFF routes (`server/api/v1/`) and JWT helpers (`server/utils/`)
- BFF-coupled composables (e.g., `useDslApi`)
- Plugin-specific Nuxt wrappers
- `module.ts` — the Nuxt module definition

### What moves to `components`
- All reusable SFCs
- Generic UI composables (e.g., `useSidebar`)
- Domain types (`dsl.ts`, `execution.ts`, `runner.ts`)
- Pure helpers/formatters

### Communication
- **Browser never calls Spring Boot directly**
- All API traffic through `admin-ui-plugin/server/api/v1/*` Nitro routes (merged into host)
- BFF manages JWT, forwards calls, translates errors
- Browser holds only session cookie; tokens server-side

### Styling
- Use Tailwind CSS everywhere
- Import shared theme via `@cbs/components/tailwind.config`
- Source of truth: `components/src/tailwind.config.ts`
- Prefer semantic tokens: `bg-background`, `text-neutral-800`, `bg-primary-500`
- No hard-coded colors in `admin-ui-plugin`

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

### Reactivity & state

- **No `watch` / `watchEffect`** for cross-component coordination. Use event emit/listen (mitt, Vue `provide`/`inject`,
  or Pinia store events). `watch` allowed only inside a single composable for narrow ref↔derived-sync on the SAME
  component scope.
- **Polling requires a user-controllable UI toggle** (pause/play button + interval selector). Never start polling
  silently. Persist toggle + interval via `useLocalStorageState`.
- **UI controls persist via `useLocalStorageState`** — sidebar collapse, drawer/modal open-close, filter selections,
  collapse sections, tab/panel visibility, sort order. Use `useNamespacedLocalStorageState` when scoping per-context.
  Never store UI state in plain `ref`s that reset on reload.

## Code Style & Readability

### Self-documenting, simple code
- Do not add explanatory comments. The code itself must be clear enough to read without them.
- Remove outdated, redundant, or obvious comments when touching a file.
- JSDoc/TSDoc is allowed on public library APIs only when the signature alone is not enough; otherwise avoid comments.
- Refactor confusing code instead of explaining it with a comment.
- Choose the simplest implementation that works. Avoid clever tricks, premature abstraction, and over-engineering.
- Keep functions, composables, and components small and focused on a single responsibility.
- Use descriptive, intention-revealing names for variables, functions, classes, and components.
- Avoid abbreviations, unclear acronyms, and magic values.
- Delete dead code, unused imports, and duplicated logic.
- Readable code is the goal; if a new teammate cannot understand it at a glance, simplify it.

## CLI Commands (run from `frontend/`)

```bash
pnpm install                 # install workspace dependencies
pnpm dev                     # start admin-ui-plugin dev server (standalone)
pnpm build                   # build admin-ui-plugin standalone
pnpm build:components        # build component library
pnpm test                    # run admin-ui-plugin tests
pnpm check                   # biome lint + format check
pnpm check:fix               # biome fix
```

Package-specific:
```bash
pnpm --filter @cbs/admin-ui-plugin dev
pnpm --filter components build
```

## End-to-End Tests (Playwright)

Smoke specs live in `frontend/e2e/`. They boot the Nuxt dev server (which
embeds the Nitro BFF) and exercise the highest-value user paths through a real
browser, asserting on the `data-testid` attributes installed across T257-T261.

```bash
pnpm test:e2e          # headless run — chromium by default
pnpm test:e2e:ui       # interactive Playwright UI mode
```

### Backend-optional

By default `pnpm test:e2e` does **not** require the Spring backend: the BFF is
pointed at a dead port (`127.0.0.1:1`) and the suite verifies the UI still
mounts correctly — filter chrome, construct explorer shell, branded error
banner — when the backend is unreachable. Specs that need real backend data
(runner happy-path preview) call `test.skip(!process.env.E2E_BACKEND, …)` and
self-skip in offline mode.

To run with a real backend:

```bash
E2E_BACKEND=1 E2E_BACKEND_URL=http://localhost:8090 pnpm test:e2e
```

`E2E_BACKEND_URL` is propagated to the Nitro BFF as `BACKEND_BASE_URL`. CI
should set both.

### Configuration

- `frontend/playwright.config.ts` — projects, webServer, reporter.
- `frontend/e2e/fixtures.ts` — shared fixtures + `backendRequired` skip helper.
- Specs use `getByTestId(...)` exclusively; never CSS class selectors.

### Browser binaries

Chromium is installed on demand via `pnpm exec playwright install chromium`.
Run it once after `pnpm install` if the sandbox blocks the auto-download.

## Agent Workflows

- **Add page**: Create Vue file in `admin-ui-plugin/app/pages/`, register the route in `module.ts` via `extendPages`. Import from `@cbs/components`.
- **Add shared component**: Build in `components/src/components/`, export from `components/src/index.ts`, import via `@cbs/components` in `admin-ui-plugin`.
- **Add API consumer**: Add Nitro route under `admin-ui-plugin/server/api/v1/`, reuse `server/utils/` helpers.
- **Change colors**: Edit `components/src/tailwind.config.ts` and `docs/colors.md`.
- **Add client state**: Add Pinia store in `admin-ui-plugin/app/stores/`.
- **Move code to components**: Update internal imports to relative paths, update `components/src/index.ts` exports, adjust `admin-ui-plugin` imports.
- **Add module option**: Extend `ModuleOptions` in `module.ts` and wire it in the `setup()` function.
- **Change route prefix**: Set `routePrefix` in the host app's `adminUiPlugin` config key.

## Key Docs

- `docs/architecture-ui.md` — full frontend/BFF architecture
- `docs/colors.md` — color palette
- `docs/frontend/index.md` — UI design overview
- `docs/frontend/layout.md` — responsive shell
- `docs/frontend/dsl-workbench.md` — DSL authoring
- `docs/frontend/runner.md` — run/preview/explain UI
- `docs/frontend/execution-details.md` — execution inspection
