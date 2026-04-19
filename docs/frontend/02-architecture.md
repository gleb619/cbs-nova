# Hexagonal Architecture & Dependency Injection

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

**Critical:** The `provide` and `inject` must come from the **same** `piqureWrapper` instance. A common bug was creating
separate wrappers — leading to `inject` returning `undefined`.

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
