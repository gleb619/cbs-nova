# Frontend Overview & Structure

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
