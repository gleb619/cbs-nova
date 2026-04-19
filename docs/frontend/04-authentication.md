# Authentication Flow

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

| File                | Path                                                                    | Role                                                             |
|---------------------|-------------------------------------------------------------------------|------------------------------------------------------------------|
| AuthConfig          | `frontend/src/app/auth/application/AuthConfig.ts`                       | Stores `localAuthMode` flag                                      |
| LocalAuthHttp       | `frontend/src/app/auth/infrastructure/secondary/LocalAuthHttp.ts`       | HTTP client for auth endpoints                                   |
| LocalAuthRepository | `frontend/src/app/auth/infrastructure/secondary/LocalAuthRepository.ts` | Implements `AuthRepository` port                                 |
| TokenStore          | `frontend-plugin/composables/auth/TokenStore.ts`                        | JWT persistence (localStorage → in-memory fallback)              |
| TokenStorage        | `frontend-plugin/composables/auth/TokenStorage.ts`                      | New strategy pattern (SessionStorage default, being migrated to) |
| useAuth             | `frontend-plugin/composables/auth/useAuth.ts`                           | Composable wrapping the DI repository                            |

### Auth Guard

Defined in `frontend/src/app/router.ts` (`beforeEach`):

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

### Default Credentials

| Username | Password | Role  |
|----------|----------|-------|
| `admin1` | `admin1` | ADMIN |
| `user1`  | `user1`  | USER  |

Defined in `backend/src/main/resources/application.yml`.
