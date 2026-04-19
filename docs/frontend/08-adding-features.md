# Adding a New Feature — Step by Step

Follow the hexagonal architecture pattern. Example: adding a "Users" feature.

### Step 1: Domain Types & Ports (`frontend-plugin/`)

```
frontend-plugin/composables/user/
├── User.ts                  # interface User { id, name, email, ... }
├── UserRepository.ts        # interface: findAll, findById, create, update
└── UserListVue.vue          # presentational component
```

### Step 2: DI Provider (`frontend-plugin/`)

Create `piqureWrapper` in `UserProvider.ts`, export `USER_REPOSITORY` key, `provideForUser()`, and `inject`.

### Step 3: HTTP Adapter (`frontend/`)

Implement `UserRepository` using `AxiosHttp` in `UserHttp.ts`. Use `TokenStorage.get()` for Bearer token.

### Step 4: Page Component (`frontend/`)

Inject `USER_REPOSITORY` via `UserProvider` in `UsersPageVue.vue`, fetch and display data.

### Step 5: Router (`frontend/`)

Define routes in `UserRouter.ts`: `/users` → `UsersPageVue`.

### Step 6: Register Route

In `frontend/src/app/router.ts`, add `...userRoutes()`.

### Step 7: Nuxt Plugin

Create `frontend/src/app/plugins/user.ts` to wire the adapter:

```typescript
export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  const repo = new UserHttp(new AxiosHttp(axios.create({ baseURL: config.public.apiBase as string })));
  provideForUser(repo);
});
```

### Step 8: Add Navigation Link

Add link to `HomepageVue.vue` or `HelpPageVue.vue`.
