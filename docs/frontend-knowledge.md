# Frontend Knowledge — CBS Nova

**Stack:** Nuxt 3.15 SPA (`ssr: false`), Vue 3.5, Tailwind CSS v4, TypeScript, piqure DI, Pinia 3, i18next 25, Axios, Biome, Vitest (100% coverage), Playwright.

**Workspaces:**
- `frontend/` — App shell, HTTP adapters, routing, DI wiring
- `frontend-plugin/` — Domain types, port interfaces, presentational Vue. **Never import from `frontend/` into plugin**

**Hexagonal Layers:**

| Layer       | Location                                               | Responsibility                           |
|-------------|--------------------------------------------------------|------------------------------------------|
| Domain      | `frontend-plugin/composables/<feature>/`               | Types, ports, components                 |
| Application | `frontend/src/app/<feature>/application/`              | Use cases, routing                       |
| Primary     | `frontend/src/app/<feature>/infrastructure/primary/`   | Page Vue components                      |
| Secondary   | `frontend/src/app/<feature>/infrastructure/secondary/` | HTTP adapters                            |
| DI Wiring   | `frontend/src/app/plugins/*.ts`                        | Nuxt plugins connecting adapters → ports |

**DI Critical Rule (piqure):**
```typescript
const { provide, inject } = piqureWrapper(window, 'piqure');
// provide/inject MUST come from same wrapper instance
```

**Auth:**
- **Local (dev):** `POST /api/public/auth/token` → sessionStorage. Creds: `admin1/admin1` (ADMIN), `user1/user1` (USER)
- **Keycloak (prod):** JWKS from realm

**Routing/Guards:**
- Routes defined per-feature, spread in `router.ts`: `...featureRoutes()`
- Guard logic: Public paths allow → `/login` redirects if auth → others require auth

**Styling:** Primary `#D4532D`, Secondary `#1A1A2E`, compact spacing (~25% smaller), base font 0.75rem

**Dev Commands:**
```bash
pnpm dev              # :3000, local auth
pnpm test             # Vitest 100% coverage enforced
pnpm e2e              # Playwright UI
pnpm lint:fix         # Biome auto-fix
./gradlew :backend:bootRun  # :7070
```

**8-Step Feature Pattern:**
1. Domain types & ports in `frontend-plugin/composables/<feature>/`
2. DI Provider with `piqureWrapper`
3. HTTP adapter in `frontend/src/app/<feature>/infrastructure/secondary/`
4. Page component (injects repository)
5. Router in `application/<Feature>Router.ts`
6. Register route in `frontend/src/app/router.ts`
7. Nuxt plugin in `frontend/src/app/plugins/<feature>.ts`
8. Add navigation link

**Troubleshooting:**
- `inject()` undefined → separate piqureWrapper instances
- API 404 → check `baseURL` in nuxt.config.ts or `SPRING_BOOT_URL`
- Build errors → run `npx nuxt prepare`
