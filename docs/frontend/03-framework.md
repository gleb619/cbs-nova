# Framework: Plugins & Routing

## 4. Nuxt Plugin System

### What is a Nuxt Plugin?

Nuxt plugins are functions that run **once** during app initialization, before the Vue app is mounted. They are defined
with `defineNuxtPlugin()` and can:

- Register Vue plugins (`app.use()`)
- Provide values to the Nuxt app (`return { provide: { ... } }`)
- Perform side effects (initialize configs, register DI providers)

### Plugin Discovery

Nuxt looks for plugins in the `~/plugins/` directory by default. This project overrides the path in `nuxt.config.ts`:

```typescript
dir: { pages: '', plugins: 'src/app/plugins' },
```

All `.ts` / `.js` files in `frontend/src/app/plugins/` are auto-registered.

### Plugin Files Explained

#### `frontend/src/app/plugins/auth.ts`

- **Purpose:** Reads `NUXT_PUBLIC_LOCAL_AUTH` env var and sets the auth mode flag.
- **When it runs:** App initialization.

#### `frontend/src/app/plugins/setting.ts`

- **Purpose:** Creates the `SettingHttp` adapter with axios configured to hit the backend.
- **DI wiring:** Calls `provideForSetting(repository)` which registers the adapter under `SETTING_REPOSITORY` in piqure.

#### `frontend/src/app/plugins/translations.ts`

- **Purpose:** Registers locale strings with i18next.

#### `frontend-plugin/plugins/auth.ts`

- **Purpose:** The **dynamic** auth plugin. Checks `config.public.localAuth` and lazy-loads `LocalAuthRepository` or
  `KeycloakAuthRepository`.

---

## 7. Routing

### Route Registration

Routes are defined per-feature and spread into the router in `frontend/src/app/router.ts`:

```typescript
export const routes = [
  { path: '/', name: 'index', component: IndexPageVue },   // from frontend-plugin
  ...helpRoutes(),                                          // /help, /privacy, /terms
  ...homeRoutes(),                                          // /home, /settings
  { path: '/login', name: 'login', component: LoginPageVue }, // from frontend-plugin
];
```

### Feature Routers

| Feature | Router File                                       | Routes                              |
|---------|---------------------------------------------------|-------------------------------------|
| Help    | `frontend/src/app/help/application/HelpRouter.ts` | `/help`, `/privacy`, `/terms`       |
| Home    | `frontend/src/app/home/application/HomeRouter.ts` | `/` → redirect `/home`, `/settings` |

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
