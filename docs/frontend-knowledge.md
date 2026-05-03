# Frontend Knowledge Base — CBS Nova

## 1. Overview

The CBS Nova admin UI is a **Vue 3 SPA** built with **Nuxt 3**, **TypeScript**, and **Tailwind CSS v4**. It uses *
*hexagonal architecture** with a split workspace:

| Workspace          | Package             | Purpose                                                                |
|--------------------|---------------------|------------------------------------------------------------------------|
| `frontend/`        | `cbs-nova`          | Main Nuxt 3 SPA — adapters, pages, routing, DI wiring                  |
| `frontend-plugin/` | `@cbs/admin-plugin` | Shared Nuxt layer — domain types, ports, presentational Vue components |

**Stack:**

- **Runtime:** Node.js ≥ 24, pnpm ≥ 10.33 (workspace monorepo)
- **Framework:** Nuxt 3.15+ (SPA mode, `ssr: false`)
- **UI:** Vue 3.5, Vue Router 5, Tailwind CSS v4
- **State:** Pinia 3 + pinia-plugin-persistedstate
- **DI:** piqure 2.2 (provide/inject dependency injection)
- **i18n:** i18next 25 + i18next-vue
- **HTTP:** Axios 1.14 wrapped in `AxiosHttp`
- **Auth:** Local Auth (dev) or Keycloak (production)
- **Lint/Format:** Biome 1.9.4
- **Test:** Vitest 4 (unit), Playwright 1.59 (E2E)
- **Build:** vue-tsc 3 + Vite 7, orchestrated by Gradle

---

## 2. Project Structure

### `frontend/` — Main Application

```
frontend/
├── app.vue                          # Root shell: loading spinner → dynamic import of main.ts
├── nuxt.config.ts                   # Nuxt config: devProxy, Tailwind, aliases, runtime config
├── package.json                     # Dependencies, scripts
├── tsconfig.json                    # Base TS config (extends @vue/tsconfig)
├── tsconfig.build.json              # Build TS config (extends .nuxt/nuxt.d.ts for Nuxt types)
├── biome.json                       # Linting + formatting rules
├── vitest.config.ts                 # Unit test configuration
├── build.gradle                     # Gradle pnpm/Vite/Nuxt tasks
│
├── public/                          # Static assets (favicons, app icons)
│
└── src/
    ├── app/
    │   ├── main.ts                  # Vue app entry: i18next init, Pinia, Vue Router mount
    │   ├── AppVue.vue               # <router-view /> wrapper
    │   ├── router.ts                # Route registration + auth guard (beforeEach)
    │   ├── injections.ts            # Shared piqureWrapper for frontend app
    │   ├── env.d.ts                 # TypeScript environment declarations
    │   │
    │   ├── assets/main.css          # Tailwind import + frontend-plugin SCSS chain
    │   │
    │   ├── plugins/                 # Nuxt plugins (auto-discovered via dir config)
    │   │   ├── auth.ts              # Initializes auth config (local vs keycloak)
    │   │   ├── setting.ts           # Wires SettingHttp adapter into piqure DI
    │   │   └── translations.ts      # Registers home translations with i18next
    │   │
    │   ├── auth/                    # Authentication feature (Local Auth mode)
    │   │   ├── application/
    │   │   │   └── AuthConfig.ts    # Auth mode toggle: setAuthConfig / isLocalAuthMode
    │   │   └── infrastructure/secondary/
    │   │       ├── LocalAuthHttp.ts # HTTP client for /api/public/auth/token
    │   │       └── LocalAuthRepository.ts # AuthRepository impl, uses TokenStore
    │   │
    │   ├── help/                    # Help & documentation feature
    │   │   ├── application/
    │   │   │   └── HelpRouter.ts    # Routes: /help, /privacy, /terms
    │   │   └── infrastructure/primary/
    │   │       ├── HelpPageVue.vue      # Dashboard-style documentation page
    │   │       ├── PrivacyPolicyVue.vue # Privacy policy page
    │   │       └── TermsOfServiceVue.vue# Terms of service page
    │   │
    │   └── home/                    # Homepage + Settings feature
    │       ├── HomeTranslations.ts  # i18next translation keys registration
    │       ├── locales/
    │       │   ├── en.ts            # English translations
    │       │   └── ru.ts            # Russian translations
    │       ├── application/
    │       │   └── HomeRouter.ts    # Routes: / → /home, /settings
    │       └── infrastructure/
    │           ├── primary/
    │           │   ├── HomepageVue.vue    # Default homepage (legacy placeholder)
    │           │   └── SettingsPageVue.vue# Settings page, injects SettingRepository
    │           └── secondary/
    │               └── SettingHttp.ts # HTTP adapter: GET /api/settings with Bearer token
    │
    ├── unit/                        # Vitest unit tests (colocated by feature)
    ├── e2e/                         # Playwright E2E tests
    ├── shared/types/                # Shared TypeScript type definitions
    ├── content/images/              # App images (Vue logo, etc.)
    └── error/404.html               # Custom 404 page
```

### `frontend-plugin/` — Shared Nuxt Layer

```
frontend-plugin/
├── nuxt.config.ts                   # Plugin layer config: extends main.scss
├── package.json                     # @cbs/admin-plugin workspace package
├── biome.json                       # Linting rules (includes *.scss)
├── tsconfig.json                    # TS config (extends @vue/tsconfig)
├── vitest.config.ts                 # Unit test configuration
├── build.gradle                     # Gradle pnpm tasks
│
├── assets/main.scss                 # Tailwind v4 @import + @theme block (design tokens)
│
├── composables/                     # Shared composables (ports + presentational components)
│   ├── auth/
│   │   ├── AuthProvider.ts          # piqure DI: AUTH_REPOSITORY key, provideForAuth, inject
│   │   ├── AuthRepository.ts        # Port interface: login, logout, authenticated, currentUser
│   │   ├── AuthenticatedUser.ts     # Type: { isAuthenticated, username, token }
│   │   ├── TokenStorage.ts          # Token persistence: InMemory + SessionStorage strategies
│   │   ├── TokenStore.ts            # Legacy token storage (localStorage fallback, being replaced)
│   │   ├── useAuth.ts               # Composable: wraps AUTH_REPOSITORY inject into useAuth()
│   │   ├── LoginPageVue.vue         # Login form component (Tailwind-styled)
│   │   └── IndexPageVue.vue         # Landing page component (index route /)
│   │
│   ├── http/
│   │   └── AxiosHttp.ts             # Generic HTTP wrapper around AxiosInstance
│   │
│   ├── i18n/
│   │   └── Translations.ts          # i18next translation registration helper
│   │
│   └── setting/
│       ├── Setting.ts               # TypeScript interface: { id, code, value, description }
│       ├── SettingListVue.vue       # Presentational list component
│       ├── SettingProvider.ts       # piqure DI: SETTING_REPOSITORY key, provideForSetting, inject
│       └── SettingRepository.ts     # Port interface: findAll(): Promise<Setting[]>
│
├── infrastructure/secondary/        # Keycloak auth (production mode)
│   ├── KeycloakAuthRepository.ts    # AuthRepository impl using Keycloak
│   └── KeycloakHttp.ts             # HTTP adapter using Keycloak instance
│
└── plugins/                         # Nuxt plugins (from plugin layer)
    ├── auth.ts                      # Dynamic auth provider: LocalAuth or Keycloak based on config
    └── i18n.ts                      # i18next + I18NextVue registration
```

---

## 3. Hexagonal Architecture

The project strictly follows **hexagonal (ports and adapters) architecture**:

### Rules

| Layer                  | Location                                               | Responsibility                                       |
|------------------------|--------------------------------------------------------|------------------------------------------------------|
| **Domain**             | `frontend-plugin/composables/<feature>/`               | TypeScript interfaces, domain types, port interfaces |
| **Application**        | `frontend/src/app/<feature>/application/`              | Use cases, routing configuration                     |
| **Primary (driving)**  | `frontend/src/app/<feature>/infrastructure/primary/`   | Page Vue components, user-facing                     |
| **Secondary (driven)** | `frontend/src/app/<feature>/infrastructure/secondary/` | HTTP adapters, external service clients              |
| **DI Wiring**          | `frontend/src/app/plugins/*.ts`                        | Nuxt plugins that connect adapters to ports          |

### Key Principle

**`frontend-plugin`** must never import from `frontend/`. It owns:

- Domain types (e.g., `Setting`, `AuthenticatedUser`)
- Port interfaces (e.g., `AuthRepository`, `SettingRepository`)
- Presentational Vue components (e.g., `LoginPageVue`, `SettingListVue`)
- DI provider setup (e.g., `AuthProvider.ts`, `SettingProvider.ts`)

**`frontend`** owns:

- HTTP adapters (e.g., `SettingHttp`, `LocalAuthHttp`)
- Page components (e.g., `SettingsPageVue`, `HelpPageVue`)
- Route definitions (e.g., `HomeRouter.ts`, `HelpRouter.ts`)
- DI wiring in Nuxt plugins

### Dependency Flow

```
SettingsPageVue.vue (primary)
  └── inject(SETTING_REPOSITORY)  ← from SettingProvider.ts
        └── SettingHttp (secondary adapter)
              └── AxiosHttp → axios → devProxy → Spring Boot :7070
```

---

## 4. Nuxt Plugin System

### What is a Nuxt Plugin?

Nuxt plugins are functions that run **once** during app initialization, before the Vue app is mounted. They are defined
with `defineNuxtPlugin()` and can:

- Register Vue plugins (`app.use()`)
- Provide values to the Nuxt app (`return { provide: { ... } }`)
- Perform side effects (initialize configs, register DI providers)

### Plugin Discovery

Nuxt looks for plugins in the `~/plugins/` directory by default. This project overrides the path in
`nuxt.config.ts` ([line 14](../frontend/nuxt.config.ts#L14)):

```typescript
dir: { pages: '', plugins: 'src/app/plugins' },
```

All `.ts`/`.js` files in `frontend/src/app/plugins/` are auto-registered.

### Plugin Files Explained

#### `frontend/src/app/plugins/auth.ts` ([source](../frontend/src/app/plugins/auth.ts))

```typescript
export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  initializeAuthConfig(config.public.localAuth);
});
```

- **Purpose:** Reads `NUXT_PUBLIC_LOCAL_AUTH` env var and sets the auth mode flag
- **When it runs:** App initialization
- **Effect:** Determines whether login uses Local Auth or Keycloak

#### `frontend/src/app/plugins/setting.ts` ([source](../frontend/src/app/plugins/setting.ts))

```typescript
export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  const repository = new SettingHttp(
    new AxiosHttp(axios.create({ baseURL: config.public.apiBase as string }))
  );
  provideForSetting(repository);
});
```

- **Purpose:** Creates the `SettingHttp` adapter with axios configured to hit the backend
- **DI wiring:** Calls `provideForSetting(repository)` which registers the adapter under `SETTING_REPOSITORY` in piqure
- **Why it matters:** `SettingsPageVue.vue` calls `inject(SETTING_REPOSITORY)` to get this instance

#### `frontend/src/app/plugins/translations.ts` ([source](../frontend/src/app/plugins/translations.ts))

```typescript
export default defineNuxtPlugin(() => {
  registerTranslations(homeTranslations);
});
```

- **Purpose:** Registers English/Russian locale strings with i18next

#### `frontend-plugin/plugins/auth.ts` ([source](../frontend-plugin/plugins/auth.ts))

- **Purpose:** The **dynamic** auth plugin. At runtime, checks `config.public.localAuth`:
    - **true:** Creates `LocalAuthRepository` + `LocalAuthHttp`, calls `provideForAuth()`
    - **false:** Creates `KeycloakAuthRepository` + `KeycloakHttp`, calls `provideForAuth()`
- **Why dynamic:** Uses `await import()` to lazy-load the correct implementation, avoiding bundling Keycloak in dev mode

#### `frontend-plugin/plugins/i18n.ts` ([source](../frontend-plugin/plugins/i18n.ts))

- **Purpose:** Initializes i18next with LanguageDetector and registers `I18NextVue` on the Vue app
- **Note:** There are **two** i18n initialization paths — one in `frontend-plugin/plugins/i18n.ts` and one in
  `frontend/src/app/main.ts`. The `main.ts` one is the active one for the SPA.

### `defineNuxtPlugin` Type Notes

`defineNuxtPlugin` is a Nuxt global that is **not** part of standard TypeScript. It is provided by Nuxt's generated
types in `.nuxt/nuxt.d.ts`. The `tsconfig.build.json` includes
`.nuxt/nuxt.d.ts` ([line 4](../frontend/tsconfig.build.json#L4)) to resolve this.

---

## 5. Dependency Injection — piqure

### How piqure Works

`piqure` is a lightweight provide/inject DI library. Each feature creates its own **isolated DI context**:

```typescript
// frontend-plugin/composables/auth/AuthProvider.ts
const { provide, inject } = piqureWrapper(
  typeof window !== 'undefined' ? window : ({} as Window),
  'piqure'
);

export const AUTH_REPOSITORY = key<AuthRepository>('AuthRepository');

export const provideForAuth = (repository: AuthRepository) => {
  provide(AUTH_REPOSITORY, repository);
};

export { inject };
```

**Why bind to `window`?** In SPA mode (`ssr: false`), `window` is always available. The wrapper uses it as a container
to store the DI registry.

**Critical:** The `provide` and `inject` must come from the **same** `piqureWrapper` instance. A common bug (fixed
in [frontend-refactoring.ignore.md](./frontend-refactoring.ignore.md)) was creating separate wrappers — leading to
`inject` returning `undefined`.

### Consumer Pattern

```typescript
// In SettingsPageVue.vue
import { inject, SETTING_REPOSITORY } from '@cbs/admin-plugin/composables/setting/SettingProvider';

const settingRepo = inject(SETTING_REPOSITORY);
const settings = await settingRepo.findAll();
```

### Registry Map

| DI Key               | Provided By                                                                | Injected By            |
|----------------------|----------------------------------------------------------------------------|------------------------|
| `AUTH_REPOSITORY`    | `frontend-plugin/plugins/auth.ts` or `frontend/src/app/plugins/setting.ts` | `useAuth()` composable |
| `SETTING_REPOSITORY` | `frontend/src/app/plugins/setting.ts`                                      | `SettingsPageVue.vue`  |

---

## 6. Authentication Flow

### Local Auth Mode (Development)

```
User enters credentials in LoginPageVue.vue
  ↓
LocalAuthRepository.login({ username, password })
  ↓
LocalAuthHttp.login(username, password)
  → POST http://localhost:7070/api/public/auth/token
  → Body: { username, password }
  → Response: { access_token, token_type, expires_in }
  ↓
TokenStore.set(access_token)     ← stored in localStorage (fallback: in-memory)
  ↓
router.push('/') → auth guard passes → HomepageVue renders
```

### Key Files

| File                | Path                                                                                                                                                | Role                                                             |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| AuthConfig          | [`frontend/src/app/auth/application/AuthConfig.ts`](../frontend/src/app/auth/application/AuthConfig.ts)                                             | Stores `localAuthMode` flag                                      |
| LocalAuthHttp       | [`frontend/src/app/auth/infrastructure/secondary/LocalAuthHttp.ts`](../frontend/src/app/auth/infrastructure/secondary/LocalAuthHttp.ts)             | HTTP client for auth endpoints                                   |
| LocalAuthRepository | [`frontend/src/app/auth/infrastructure/secondary/LocalAuthRepository.ts`](../frontend/src/app/auth/infrastructure/secondary/LocalAuthRepository.ts) | Implements `AuthRepository` port                                 |
| TokenStore          | [`frontend-plugin/composables/auth/TokenStore.ts`](../frontend-plugin/composables/auth/TokenStore.ts)                                               | JWT persistence (localStorage → in-memory fallback)              |
| TokenStorage        | [`frontend-plugin/composables/auth/TokenStorage.ts`](../frontend-plugin/composables/auth/TokenStorage.ts)                                           | New strategy pattern (SessionStorage default, being migrated to) |
| useAuth             | [`frontend-plugin/composables/auth/useAuth.ts`](../frontend-plugin/composables/auth/useAuth.ts)                                                     | Composable wrapping the DI repository                            |

### Auth Guard

Defined in [`frontend/src/app/router.ts`](../frontend/src/app/router.ts) (`beforeEach`):

1. **Public paths** (`/`, `/help`, `/privacy`, `/terms`) — always allowed
2. **`/login`** — redirects to `/` if already authenticated
3. **All other routes** — require authentication
    - Local Auth mode → redirect to `/login`
    - Keycloak mode → trigger `auth.login()` (Keycloak redirect)

### Token Storage Migration (TODO)

The codebase is migrating from `TokenStore` (legacy, localStorage-based) to `TokenStorage` (new, strategy pattern):

| Strategy                     | Persistence      | Survives refresh? | Use case                                 |
|------------------------------|------------------|-------------------|------------------------------------------|
| `InMemoryTokenStorage`       | Variable         | No                | Unit tests, stateless dev                |
| `SessionStorageTokenStorage` | `sessionStorage` | Yes               | **Default** — safe SPA auth              |
| `TokenStore` (legacy)        | `localStorage`   | Yes               | Current default in `LocalAuthRepository` |

**Status:** `LocalAuthRepository` still uses `TokenStore`. Migration pending —
see [frontend-login-feature.ignore.md](./frontend-login-feature.ignore.md).

### Default Credentials

| Username | Password | Role  |
|----------|----------|-------|
| `admin1` | `admin1` | ADMIN |
| `user1`  | `user1`  | USER  |

Defined in `backend/src/main/resources/application.yml`.

---

## 7. Routing

### Route Registration

Routes are defined per-feature and spread into the router in [
`frontend/src/app/router.ts`](../frontend/src/app/router.ts):

```typescript
export const routes = [
  { path: '/', name: 'index', component: IndexPageVue },   // from frontend-plugin
  ...helpRoutes(),                                          // /help, /privacy, /terms
  ...homeRoutes(),                                          // /home, /settings
  { path: '/login', name: 'login', component: LoginPageVue }, // from frontend-plugin
];
```

### Feature Routers

| Feature | Router File                                                                                             | Routes                              |
|---------|---------------------------------------------------------------------------------------------------------|-------------------------------------|
| Help    | [`frontend/src/app/help/application/HelpRouter.ts`](../frontend/src/app/help/application/HelpRouter.ts) | `/help`, `/privacy`, `/terms`       |
| Home    | [`frontend/src/app/home/application/HomeRouter.ts`](../frontend/src/app/home/application/HomeRouter.ts) | `/` → redirect `/home`, `/settings` |

### Route Guards

```
Request → beforeEach → Public? → Yes → Allow
                      ↓ No
                  Is /login? → Already auth? → Yes → Redirect /
                               ↓ No → Allow
                      ↓ Not /login
                  Authenticated? → No → Local Auth? → /login
                                              → Keycloak? → auth.login()
                              → Yes → Allow
```

---

## 8. Styling — Tailwind CSS v4

### CSS Import Chain

```
frontend/src/app/assets/main.css
  → @import "tailwindcss"
  → @import "../../../../frontend-plugin/assets/main.scss"
  → @import "tailwindcss" (in SCSS)
  → @theme { ... } (custom design tokens)
```

The main CSS file (`frontend/src/app/assets/main.css`, [source](../frontend/src/app/assets/main.css)) imports Tailwind
and then the plugin's SCSS. The `@source` directives tell Tailwind to scan plugin files for class usage:

```css
@import "tailwindcss";
@import "../../../../frontend-plugin/assets/main.scss";

@source "../../../../frontend-plugin/**/*.vue";
@source "./**/*.vue";
@source "./**/*.ts";
```

### Design Tokens

Defined in [`frontend-plugin/assets/main.scss`](../frontend-plugin/assets/main.scss) `@theme` block:

| Token             | Value                                   | Usage                                                      |
|-------------------|-----------------------------------------|------------------------------------------------------------|
| **Primary color** | `#D4532D` (terracotta/rust)             | `bg-primary-600`, `text-primary-600`, `border-primary-600` |
| **Secondary**     | `#1A1A2E` (navy)                        | `bg-secondary-900`                                         |
| **Neutral**       | Warm gray scale (`#FAFAF9` → `#1C1917`) | `bg-neutral-50`, `text-neutral-900`                        |
| **Success**       | `#059669` (emerald)                     | `text-success-600`                                         |
| **Danger**        | Red scale                               | `bg-danger-50`, `border-danger-200`                        |
| **Warning**       | Amber                                   | `text-warning-500`                                         |
| **Base font**     | `0.75rem` (12px)                        | `text-base`                                                |
| **Spacing**       | Compact scale (~25% reduction)          | `p-3` ≈ 0.5625rem                                          |
| **Border radius** | Tighter                                 | `rounded-sm` = 0.125rem, `rounded-lg` = 0.375rem           |
| **Shadows**       | Rust-tinted                             | `shadow-card`, `shadow-rust`, `shadow-elevated`            |

### Vite Configuration

Tailwind v4 uses the Vite plugin (not PostCSS). Configured in
`frontend/nuxt.config.ts` ([line 18](../frontend/nuxt.config.ts#L18)):

```typescript
vite: {
  plugins: [tsconfigPaths(), tailwindcss()],
  // ...
}
```

---

## 9. State Management

### Pinia

Pinia 3 is initialized in [`frontend/src/app/main.ts`](../frontend/src/app/main.ts):

```typescript
const pinia = createPinia();
pinia.use(piniaPersist);  // persistence via pinia-plugin-persistedstate
app.use(pinia);
```

The `pinia-plugin-persistedstate` plugin automatically persists Pinia store state to `localStorage`/`sessionStorage`
based on store configuration.

### piqure DI

For repository/service injection, piqure is used instead of Pinia. This keeps the hexagonal architecture clean —
repositories are injected, not stored in reactive state.

---

## 10. i18n — Internationalization

### Setup

- **Library:** i18next 25 + i18next-browser-languagedetector + i18next-vue
- **Initialization:** In `main.ts` (frontend) and `i18n.ts` plugin (frontend-plugin)
- **Fallback:** English (`en`)
- **Languages:** English (`en.ts`), Russian (`ru.ts`)

### Translation Files

| File         | Path                                                                                        |
|--------------|---------------------------------------------------------------------------------------------|
| Home EN      | [`frontend/src/app/home/locales/en.ts`](../frontend/src/app/home/locales/en.ts)             |
| Home RU      | [`frontend/src/app/home/locales/ru.ts`](../frontend/src/app/home/locales/ru.ts)             |
| Registration | [`frontend/src/app/home/HomeTranslations.ts`](../frontend/src/app/home/HomeTranslations.ts) |

### Usage in Vue

```vue
<template>
  <h2>{{ $t('home.translationEnabled') }}</h2>
</template>
```

**Known issue:** `$t` is not typed in Vue components — `tsconfig.build.json` doesn't include i18n Vue component type
augmentation. Causes `vue-tsc` error #1 (see Known Issues).

---

## 11. HTTP Layer & API Proxy

### Dev Proxy

Nuxt dev server proxies API requests to Spring Boot. Configured in
`nuxt.config.ts` ([line 29](../frontend/nuxt.config.ts#L29)):

```typescript
nitro: {
  devProxy: {
    '/api': {
      target: process.env.SPRING_BOOT_URL ?? 'http://localhost:7070',
      changeOrigin: true,
    },
  },
},
```

**Flow:** `localhost:3000/api/*` → Nuxt devProxy → `localhost:7070/api/*`

### Axios Configuration

All HTTP clients use `axios.create({ baseURL: config.public.apiBase })` where `apiBase` defaults to
`http://localhost:7070`.

**Important:** In SPA mode (`ssr: false`), Nitro's devProxy only handles SSR/server requests. Client-side axios calls go
**directly** to the `baseURL`. That's why `baseURL` is explicitly set — without it, requests go to `localhost:3000` (
wrong).

### Bearer Token

The `SettingHttp` adapter attaches the Bearer token from `TokenStore`:

```typescript
// SettingHttp.ts
const token = TokenStore.get();
const response = await this.http.get<Setting[]>('/api/settings', {
  headers: { Authorization: `Bearer ${token}` },
});
```

### Runtime Config

| Key                | Default                 | Env Override                  |
|--------------------|-------------------------|-------------------------------|
| `public.apiBase`   | `http://localhost:7070` | `SPRING_BOOT_URL`             |
| `public.localAuth` | `false`                 | `NUXT_PUBLIC_LOCAL_AUTH=true` |

---

## 12. Tools Available

### Linting & Formatting — Biome

**Config:** [`frontend/biome.json`](../frontend/biome.json)

| Command         | Description                   |
|-----------------|-------------------------------|
| `pnpm lint`     | Check all files               |
| `pnpm lint:fix` | Auto-fix formatting + imports |

**Key rules:**

- Line width: 140
- Single quotes, always semicolons
- `noUnusedImports: error`
- `useImportType: error` (prefer `import type`)
- `noExplicitAny: off`
- `useExhaustiveDependencies: off`

### Unit Testing — Vitest

**Config:** [`frontend/vitest.config.ts`](../frontend/vitest.config.ts)

| Command              | Description        |
|----------------------|--------------------|
| `pnpm test`          | Run all unit tests |
| `pnpm test:coverage` | Run with coverage  |

- **Environment:** jsdom
- **Assertions:** `@vue/test-utils` + Vitest globals
- **Coverage threshold:** 100% (enforced)
- **Test location:** `src/unit/**/*.spec.ts`

### E2E Testing — Playwright

**Config:** [`frontend/src/e2e/playwright.config.ts`](../frontend/src/e2e/playwright.config.ts)

| Command             | Description        |
|---------------------|--------------------|
| `pnpm e2e`          | Open Playwright UI |
| `pnpm e2e:headless` | Headless mode (CI) |

- **Browsers:** Chromium, Firefox, WebKit
- **Mobile:** iPhone 15, Pixel 7

### Browser DevTools

| Tool                | Access                                                                   |
|---------------------|--------------------------------------------------------------------------|
| Nuxt DevTools       | Bottom-left button in dev mode, or `http://localhost:3000/__devtools__/` |
| Vue DevTools        | Chrome extension — detects Vue 3 app                                     |
| chrome-devtools MCP | CLI tool for automated browser interaction                               |

### Architecture Testing

**Config:** [`frontend/arch-unit-ts.json`](../frontend/arch-unit-ts.json)

ArchUnit-style architecture validation. Ensures hexagonal boundaries are respected.

---

## 13. Commands Reference

### Development

| Command                      | Directory   | Description                                   |
|------------------------------|-------------|-----------------------------------------------|
| `pnpm install`               | repo root   | Install all workspace dependencies            |
| `pnpm dev`                   | `frontend/` | Start dev server (Local Auth mode) on `:3000` |
| `pnpm serve`                 | `frontend/` | Start dev server (Keycloak mode)              |
| `pnpm dev:keycloak`          | `frontend/` | Start with Keycloak explicitly                |
| `./gradlew :backend:bootRun` | repo root   | Spring Boot on `:7070`                        |

### Build

| Command                        | Directory   | Description                         |
|--------------------------------|-------------|-------------------------------------|
| `pnpm build`                   | `frontend/` | `vue-tsc` + `nuxt build`            |
| `pnpm preview`                 | `frontend/` | Preview production build on `:3000` |
| `./gradlew :frontend:assemble` | repo root   | Full frontend build via Gradle      |
| `./gradlew assemble`           | repo root   | Backend + frontend full build       |

### Test

| Command              | Directory          | Description           |
|----------------------|--------------------|-----------------------|
| `pnpm test`          | `frontend/`        | Run Vitest unit tests |
| `pnpm test:coverage` | `frontend/`        | With coverage         |
| `pnpm test`          | `frontend-plugin/` | Plugin unit tests     |
| `pnpm e2e`           | `frontend/`        | Playwright UI         |
| `pnpm e2e:headless`  | `frontend/`        | Headless E2E          |

### Lint

| Command                    | Directory   | Description      |
|----------------------------|-------------|------------------|
| `pnpm lint`                | `frontend/` | Biome check      |
| `pnpm lint:fix`            | `frontend/` | Biome auto-fix   |
| `./gradlew :frontend:lint` | repo root   | Biome via Gradle |

### Environment Variables

| Variable                 | Default                 | Effect                             |
|--------------------------|-------------------------|------------------------------------|
| `SPRING_BOOT_URL`        | `http://localhost:7070` | Backend URL for axios + devProxy   |
| `NUXT_PUBLIC_LOCAL_AUTH` | `false`                 | Enable Local Auth mode when `true` |

---

## 14. Adding a New Feature — Step by Step

Follow the hexagonal architecture pattern. Example: adding a "Users" feature.

### Step 1: Domain Types & Ports (`frontend-plugin/`)

```
frontend-plugin/composables/user/
├── User.ts                  # interface User { id, name, email, ... }
├── UserRepository.ts        # interface: findAll, findById, create, update
└── UserListVue.vue          # presentational component
```

### Step 2: DI Provider (`frontend-plugin/`)

```
frontend-plugin/composables/user/UserProvider.ts
```

Create `piqureWrapper`, export `USER_REPOSITORY` key, `provideForUser()`, and `inject`.

### Step 3: HTTP Adapter (`frontend/`)

```
frontend/src/app/user/infrastructure/secondary/UserHttp.ts
```

Implement `UserRepository` using `AxiosHttp`. Use `TokenStorage.get()` for Bearer token.

### Step 4: Page Component (`frontend/`)

```
frontend/src/app/user/infrastructure/primary/UsersPageVue.vue
```

Inject `USER_REPOSITORY` via `UserProvider`, fetch and display data.

### Step 5: Router (`frontend/`)

```
frontend/src/app/user/application/UserRouter.ts
```

Define routes: `/users` → `UsersPageVue`.

### Step 6: Register Route

In `frontend/src/app/router.ts`, add `...userRoutes()`.

### Step 7: Nuxt Plugin

```
frontend/src/app/plugins/user.ts
```

```typescript
export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  const repo = new UserHttp(
    new AxiosHttp(axios.create({ baseURL: config.public.apiBase as string }))
  );
  provideForUser(repo);
});
```

### Step 8: Add Navigation Link

Add `<router-link to="/users">Users</router-link>` to `HomepageVue.vue` or `HelpPageVue.vue`.

---

## 15. Known Issues & TODOs

### TypeScript Build Errors (4 errors)

`pnpm build` fails with `vue-tsc` errors:

| # | File                                                                                      | Error                                                | Fix                                      |
|---|-------------------------------------------------------------------------------------------|------------------------------------------------------|------------------------------------------|
| 1 | [`HomepageVue.vue:9`](../frontend/src/app/home/infrastructure/primary/HomepageVue.vue#L9) | `Property '$t' does not exist` — i18n `$t` not typed | Add i18n Vue component type augmentation |
| 2 | [`home/package-info.ts:1`](../frontend/src/app/home/package-info.ts)                      | `Cannot find module '@/BusinessContext'`             | Create file or remove import             |
| 3 | [`setting.ts:6`](../frontend/src/app/plugins/setting.ts#L6)                               | `Cannot find name 'defineNuxtPlugin'`                | Nuxt types should be in `.nuxt/types/`   |
| 4 | [`translations.ts:4`](../frontend/src/app/plugins/translations.ts#L4)                     | `Cannot find name 'defineNuxtPlugin'`                | Same as #3                               |

**Root cause:** `tsconfig.build.json` needs proper Nuxt type references. The `.nuxt/nuxt.d.ts` is included but may not
generate all types during `vue-tsc`.

### TokenStorage Migration (from frontend-login-feature)

- [ ] Update `LocalAuthRepository.ts` — replace all `TokenStore.*` calls with `TokenStorage.*` (5 locations)
- [ ] Update `SettingHttp.ts` — replace `TokenStore.get()` with `TokenStorage.get()`
- [ ] Delete old `TokenStore.ts`
- [ ] Update `LocalAuthRepository.spec.ts` — replace imports

### Keycloak Integration

- [ ] `KeycloakHttp.ts` — pass `baseURL` to axios instance (same pattern as LocalAuth)
- [ ] Verify Keycloak token flow uses `TokenStorage`

### Security

- [ ] Implement `SecureCookieTokenStorage` (HttpOnly cookies — token never exposed to JS)
- [ ] Add token expiry check to `authenticated()` with silent refresh (5 min before expiry)
- [ ] Add refresh-token endpoint call

### UX

- [ ] Login form — add loading spinner during submit
- [ ] Error messages — differentiate "invalid credentials" (401) from "network error" (5xx)
- [ ] Remember-me toggle for session persistence

### Testing

- [ ] E2E test: login → verify token in sessionStorage → navigate → verify Bearer header
- [ ] Unit test: `SessionStorageTokenStorage` — mock `sessionStorage`
- [ ] Unit test: `InMemoryTokenStorage` — verify reset between tests

### Production

- [ ] Replace `http://localhost:7070` with environment variable for production build
- [ ] Add health-check endpoint polling
- [ ] Add logout confirmation dialog

### Homepage Refactor

- [ ] `HomepageVue.vue` is a legacy placeholder with old styles. Needs Tailwind redesign matching the index page (
  `IndexPageVue.vue`) styling.

---

## 16. Browser Verification — Live Screenshots

### Index Page (`/`)

The root route renders the `IndexPageVue` component from `frontend-plugin`:

- CBS Nova logo, title, tagline
- "View Documentation" → `/help`
- "Launch Dashboard" → `/home`
- Footer with Privacy, Terms, Help links
- Render time: ~580ms (dev mode)

**Screenshot:** [index-page.png](./screenshots/index-page.png)

### Help Page (`/help`)

Structured documentation dashboard:

- Quick Navigation cards (Launch App, Login, Settings)
- Project Overview (Architecture, Frontend Stack, Backend Stack, Auth)
- Development Setup (scripts, server URLs, credentials)
- API & Type Generation section
- Render time: ~345ms

### Login Page (`/login`)

Clean Tailwind-styled form:

- Username input
- Password input
- Sign In button
- Default credentials hint: `admin1/admin1`
- Render time: ~236ms

---

## 17. Quick Troubleshooting

| Problem                              | Cause                               | Fix                                                       |
|--------------------------------------|-------------------------------------|-----------------------------------------------------------|
| `pnpm dev` fails to start            | Missing dependencies                | Run `pnpm install` from repo root                         |
| Tailwind classes not rendering       | CSS not imported                    | Check `main.css` imports Tailwind + plugin SCSS           |
| `inject(X)` returns undefined        | Separate piqureWrapper instances    | Ensure `provide` and `inject` come from same wrapper      |
| API calls return 404                 | axios `baseURL` not set             | Check `nuxt.config.ts` `runtimeConfig.public.apiBase`     |
| CORS errors in browser               | Spring Security missing CORS config | Check `SecurityConfig.java` `corsConfigurationSource()`   |
| `defineNuxtPlugin` not found by TS   | Nuxt types not generated            | Run `nuxt prepare` or `pnpm postinstall`                  |
| `@vue/tsconfig` unresolved in plugin | pnpm workspace dep issue            | Ensure `@vue/tsconfig` is in `peerDependencies` of plugin |
