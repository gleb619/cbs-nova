# CLAUDE.md

This file provides guidance to AI coding agents when working with code in this repository.

## 1. Project Overview

CBS-Nova is a **business process orchestration engine** for core banking. It replaces Spring-bean orchestration with
a Temporal + PostgreSQL backend and a Kotlin Script DSL for business rules. Non-developers author rules in `.kts` files;
the engine compiles and executes them.

**Stack:** Java 25 · Spring Boot · Temporal · PostgreSQL · Kotlin Script (.kts) · Vue 3 admin UI · Nuxt 3 SPA · Tailwind
CSS v4 · piqure DI · i18next · Biome · Gradle multi-module

> This repo is currently in design phase. `docs/` contains Technical Design Documents; no production implementation code
> exists yet.

## 2. Gradle Multi-Module Structure

| Module            | Type            | Purpose                                                                         |
|-------------------|-----------------|---------------------------------------------------------------------------------|
| `backend`         | Spring Boot app | Entry point, security, OpenAPI, local auth controller                           |
| `starter`         | Library JAR     | Settings CRUD auto-configured via `AutoConfiguration.imports`                   |
| `client`          | Library JAR     | Generated Feign + TypeScript clients from OpenAPI spec                          |
| `frontend`        | Nuxt 3 SPA      | Main app, adapters, routing, DI wiring (`ssr: false`)                           |
| `frontend-plugin` | Nuxt layer      | Shared domain types, ports, presentational Vue components (`@cbs/admin-plugin`) |

**Dependency graph:** `backend → starter` · `other app → client → backend`

Version catalog: `gradle/libs.versions.toml` — all versions, libraries, bundles, plugins defined here.

## 3. Quick Start

```bash
# 1. Start DB
docker compose up -d

# 2. Backend (port 7070)
./gradlew :backend:bootRun

# 3. Get JWT token
curl -s -X POST http://localhost:7070/api/public/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin1"}' | jq .access_token

# 4. Frontend (port 3000) — run from repo root
source ~/.nvm/nvm.sh && nvm use v22.20.0
cd frontend && pnpm dev
```

**Credentials:** `admin1/admin1` (ADMIN), `user1/user1` (USER). Defined in `backend/src/main/resources/application.yml`.

**Swagger UI:** http://localhost:7070/swagger-ui.html

## 4. Backend — Key Files

| File                                                                | Purpose                                                                            |
|---------------------------------------------------------------------|------------------------------------------------------------------------------------|
| `backend/src/main/java/cbs/app/CbsApp.java`                         | `@SpringBootApplication` entry point, scans `cbs.app.*`                            |
| `backend/src/main/java/cbs/app/config/SecurityConfig.java`          | JWT decoders, CORS, security filter chain, conditional local/Keycloak beans        |
| `backend/src/main/java/cbs/app/controller/LocalAuthController.java` | `POST /api/public/auth/token` — local RSA JWT auth                                 |
| `backend/src/main/java/cbs/app/config/OpenApiConfig.java`           | OpenAPI/Swagger metadata, JWT + OAuth2 security schemes                            |
| `backend/src/main/resources/application.yml`                        | Datasource, JPA, Flyway, logging, `app.local-auth` users                           |
| `starter/src/main/java/cbs/nova/config/NovaAutoConfiguration.java`  | `@AutoConfiguration` + `@ComponentScan` + `@EntityScan` + `@EnableJpaRepositories` |
| `starter/.../META-INF/spring/...AutoConfiguration.imports`          | Spring Boot auto-discovery registration                                            |

## 5. Authentication

Two modes controlled by `app.keycloak.enabled` in `application.yml`:

**Local (dev, default `app.keycloak.enabled=false`):**

- RSA key pair from classpath: `local-jwt-pkcs8.pem` (private), `local-jwt-public.pem` (public)
- `POST /api/public/auth/token` → validate via in-memory users → return JWT (RSA-signed, 1h)
- JWT issuer claim must match `spring.application.name` = `"cbs-nova"`
- `NoOpPasswordEncoder` for plaintext dev passwords

**Keycloak (prod, `app.keycloak.enabled=true`):**

- `NimbusJwtDecoder` fetches JWKS from `keycloak.auth-server-url` + `keycloak.realm`
- Local auth controller disabled via `@ConditionalOnProperty`

**Public endpoints (no auth):** `/api/public/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`, all
`OPTIONS`.

**Stateless:** No HTTP session — every request needs JWT.

## 6. Settings API (api example)

| Method   | Path                        | Body               | Status | Description        |
|----------|-----------------------------|--------------------|--------|--------------------|
| `GET`    | `/api/settings`             | —                  | 200    | List all           |
| `GET`    | `/api/settings/{id}`        | —                  | 200    | Get by ID          |
| `GET`    | `/api/settings/code/{code}` | —                  | 200    | Get by unique code |
| `POST`   | `/api/settings`             | `SettingCreateDto` | 201    | Create             |
| `PUT`    | `/api/settings/{id}`        | `SettingUpdateDto` | 200    | Update             |
| `DELETE` | `/api/settings/{id}`        | —                  | 204    | Delete             |

**Errors:** 400 validation, 401 no JWT, 404 not found (`SettingExceptionHandler`), 409 duplicate code, 422 validation.

## 7. Database

```yaml
spring:
  datasource.url: jdbc:postgresql://localhost:5432/cbsnova
  datasource.username: cbsnova
  jpa.hibernate.ddl-auto: validate   # schema from Flyway, not Hibernate
  flyway.locations: classpath:db/migration
```

**Migrations** in `starter/src/main/resources/db/migration/`:

- `V20260101101010__init.sql` — initial schema
- `V20260410120000__create_settings.sql` — `settings(id, code UNIQUE, value, description)`

**Docker:** `docker-compose.yml` includes `docker/postgresql.yml` + `docker/keycloak.yml`.

## 8. Build & Code Quality

```bash
./gradlew spotlessApply              # Format (Google Java Format)
./gradlew checkstyleMain             # Checkstyle
./gradlew check                      # compile + test + checkstyle + spotless
./gradlew build                      # full build
./gradlew generateAllClients         # TypeScript + Feign client generation
./gradlew :backend:exportOpenApi     # → backend/build/openapi.json
./gradlew :starter:generateTsClient  # → starter/generated-ts/
./gradlew :client:generateFeignClient # → client/
```

**Gradle scripts:**

- `gradle/java.gradle` (Java 25 toolchain, Lombok, MapStruct annotation processing),
- `gradle/test.gradle` (test + integrationTest source sets, test-logger plugin),
- `gradle/code-style.gradle` (Spotless with Google Java Format + import ordering),
- `gradle/checkstyle.gradle` (custom rules, relaxed for tests).

**OpenAPI & Code Generation:**

- Swagger UI: http://localhost:7070/swagger-ui.html · Spec: http://localhost:7070/v3/api-docs
- TypeScript client: `./gradlew :starter:generateTsClient` → `starter/generated-ts/` (api/ + models/)
- Java Feign client: `./gradlew :client:generateFeignClient` → `client/`
- Export OpenAPI: `./gradlew :backend:exportOpenApi` → `backend/build/openapi.json`
- Generate all: `./gradlew generateAllClients`

## 9. Testing (Backend)

Source sets: `test` (unit, nothing required), `integrationTest` (Docker/Testcontainers).

```bash
./gradlew :backend:test                                              # unit
./gradlew :backend:integrationTest                                   # needs Docker
./gradlew :backend:test --tests "*shouldReturn200WhenValidCreds"    # single method
```

**Conventions:**

- Class: `*Test` (unit), `*IntegrationTest` (integration)
- Method: `shouldXxxWhenYyy` + `@DisplayName` required on every test
- Use `@MockitoBean` (not deprecated `@MockBean`) — Spring Boot 4.x
- Controllers: `@WebMvcTest` + `@MockitoBean SecurityFilterChain`
- Integration: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `jdbc:tc:postgresql:18.3:///cbsnova`
- ArchUnit rules in `MainConventions.java`: naming, layering, no Controller→Repository direct access

## 10. Frontend — Hexagonal Architecture

Two workspaces, strict boundary:

| Layer              | Location                                               | Owns                                                                                |
|--------------------|--------------------------------------------------------|-------------------------------------------------------------------------------------|
| Domain/Ports       | `frontend-plugin/composables/<feature>/`               | TypeScript interfaces, port interfaces, presentational Vue components, DI providers |
| Application        | `frontend/src/app/<feature>/application/`              | Use cases, route configs                                                            |
| Primary (driving)  | `frontend/src/app/<feature>/infrastructure/primary/`   | Page Vue components                                                                 |
| Secondary (driven) | `frontend/src/app/<feature>/infrastructure/secondary/` | HTTP adapters                                                                       |
| DI Wiring          | `frontend/src/app/plugins/*.ts`                        | Nuxt plugins connecting adapters to ports                                           |

**Rule:** `frontend-plugin` must NEVER import from `frontend/`.

## 11. Frontend — Dependency Injection (piqure)

Each feature creates an isolated DI context on `window`:

```typescript
// e.g. frontend-plugin/composables/setting/SettingProvider.ts
const { provide, inject } = piqureWrapper(window, 'piqure');
export const SETTING_REPOSITORY = key<SettingRepository>('SettingRepository');
export const provideForSetting = (r: SettingRepository) => provide(SETTING_REPOSITORY, r);
export { inject };
```

**Critical:** `provide` and `inject` must come from the **same** `piqureWrapper` instance — mixing instances returns
`undefined`.

DI wiring in `frontend/src/app/plugins/<feature>.ts` (Nuxt plugin, runs once at app init):

```typescript
export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  provideForSetting(new SettingHttp(new AxiosHttp(axios.create({ baseURL: config.public.apiBase }))));
});
```

## 12. Frontend — Authentication Flow

Auth mode set by env var `NUXT_PUBLIC_LOCAL_AUTH=true` (default: false/Keycloak).

**Local dev flow:**

```
LoginPageVue → LocalAuthRepository.login() → LocalAuthHttp → POST /api/public/auth/token
→ token stored in TokenStore (localStorage) → router.push('/') → guard passes
```

**Auth guard** (`frontend/src/app/router.ts` `beforeEach`):

- Public paths `/`, `/help`, `/privacy`, `/terms` → always allow
- `/login` + already authenticated → redirect `/`
- Protected route + not authenticated → `/login` (local) or `auth.login()` (Keycloak)

**Token storage migration (in progress):** `TokenStore` (localStorage) → `SessionStorageTokenStorage`.

## 13. Frontend — Routing

Routes registered per feature, spread in `frontend/src/app/router.ts`:

```typescript
export const routes = [
  { path: '/', name: 'index', component: IndexPageVue },   // landing (frontend-plugin)
  ...helpRoutes(),          // /help /privacy /terms
  ...homeRoutes(),          // /home /settings
  { path: '/login', name: 'login', component: LoginPageVue },
];
```

Feature router files: `frontend/src/app/<feature>/application/<Feature>Router.ts`.

## 14. Frontend — Styling (Tailwind CSS v4)

CSS import chain:

```
frontend/src/app/assets/main.css
  → @import "tailwindcss"
  → @import "../../../../frontend-plugin/assets/main.scss"
      → @theme { primary: #D4532D, secondary: #1A1A2E, neutral warm-gray, ... }
```

Vite plugin (not PostCSS): configured in `frontend/nuxt.config.ts`.
Key tokens: primary `#D4532D` (terracotta), secondary `#1A1A2E` (navy), compact spacing (~25% smaller).

## 15. Frontend — Commands & Env Vars

```bash
pnpm install                       # from repo root (workspace)
cd frontend && pnpm dev            # :3000, local auth mode
cd frontend && pnpm serve          # :3000, Keycloak mode
cd frontend && pnpm build          # vue-tsc + nuxt build
./gradlew :frontend:assemble       # via Gradle
cd frontend && pnpm test           # Vitest (jsdom, 100% coverage enforced)
cd frontend && pnpm e2e            # Playwright (Chromium/Firefox/WebKit/mobile)
cd frontend && pnpm lint           # Biome check
cd frontend && pnpm lint:fix       # Biome auto-fix

# Always activate nvm first:
source ~/.nvm/nvm.sh && nvm use v22.20.0
```

**Env vars:** `SPRING_BOOT_URL` (default `http://localhost:7070`), `NUXT_PUBLIC_LOCAL_AUTH=true`.

**HTTP:** SPA mode `ssr: false` — client-side axios calls go directly to `baseURL` (not devProxy). Set `baseURL`
explicitly.

## 16. Adding a New Feature (Hexagonal)

7-step pattern:

1. **`frontend-plugin/composables/<feature>/`** — `<Feature>.ts` (interface), `<Feature>Repository.ts` (port),
   `<Feature>ListVue.vue` (presentational)
2. **`frontend-plugin/composables/<feature>/<Feature>Provider.ts`** — piqureWrapper, DI key, `provideFor<Feature>()`,
   `inject`
3. **`frontend/src/app/<feature>/infrastructure/secondary/<Feature>Http.ts`** — implements port using `AxiosHttp`,
   Bearer from `TokenStorage.get()`
4. **`frontend/src/app/<feature>/infrastructure/primary/<Feature>PageVue.vue`** — inject repository from Provider,
   display data
5. **`frontend/src/app/<feature>/application/<Feature>Router.ts`** — route definitions
6. **`frontend/src/app/router.ts`** — add `...<feature>Routes()`
7. **`frontend/src/app/plugins/<feature>.ts`** — Nuxt plugin wiring adapter → port

## 17. CBS-Nova Domain Concepts

| Entity            | Role                                                                                                            |
|-------------------|-----------------------------------------------------------------------------------------------------------------|
| **Workflow**      | Temporal-backed state machine. All executions backed by Temporal. Stateless events use stub wrapper.            |
| **Event**         | Triggered operation inside a workflow state. Has `context{}`, `display{}`, `transactions{}`, `finish{}` blocks. |
| **Transaction**   | Unit of work: `preview()` / `execute()` / `rollback()`. Rollback is a compensating entry (real reversal).       |
| **Helper**        | Spring bean or inline DSL (SQL/HTTP). Typed as `HelperFunction<I, O>`.                                          |
| **MassOperation** | Batch orchestration over a dataset. Emits `PARTIAL`/`COMPLETED` signals for chaining.                           |
| **Condition**     | Reusable boolean DSL block across events.                                                                       |

**DSL:** Kotlin Script `.kts` files in `cbs-rules` Gitea repo. Compile to JAR via Gradle; dev uses JSR-223.
**Versioning:** `{semver}-{gitCommitShort}`, instances locked to their start version.
**Single API endpoint:** `POST /api/events/execute` (hides workflow/event distinction, callers use `eventNumber`).
