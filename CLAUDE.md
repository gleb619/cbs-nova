# CLAUDE.md

Guidance for AI coding agents working in this repository.

## 1. Project Overview

CBS-Nova is a **business process orchestration engine** for core banking. It replaces Spring-bean orchestration with
a Temporal + PostgreSQL backend and a Java DSL for business rules. Non-developers author rules in `.java` DSL files;
the engine compiles and executes them.

**Stack:** Java 25 · Spring Boot 4 · Temporal · PostgreSQL · Java DSL · Vue 3 · Nuxt 3 SPA · Tailwind
CSS v4 · piqure DI · i18next · Biome · Gradle multi-module

> Design phase: `docs/` contains Technical Design Documents. Implementation is in progress (see `docs/plan.md`).

## 2. Quick Start

```bash
docker compose up -d                                          # 1. Start DB
./gradlew :backend:bootRun                                    # 2. Backend :7070

# 3. Get JWT
curl -s -X POST http://localhost:7070/api/public/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin1"}' | jq .access_token

# 4. Frontend :3000
source ~/.nvm/nvm.sh && nvm use v22.20.0
cd frontend && pnpm dev
```

**Credentials:** `admin1/admin1` (ADMIN), `user1/user1` (USER) — defined in `application.yml`.
**Swagger UI:** http://localhost:7070/swagger-ui.html

## 3. Gradle Modules

| Module            | Type            | Purpose                                                        |
|-------------------|-----------------|----------------------------------------------------------------|
| `backend`         | Spring Boot app | Entry point, security, OpenAPI, local auth controller          |
| `starter`         | Library JAR     | Settings CRUD, auto-configured via `AutoConfiguration.imports` |
| `client`          | Library JAR     | Generated Feign + TypeScript clients from OpenAPI spec         |
| `frontend`        | Nuxt 3 SPA      | App shell, adapters, routing, DI wiring (`ssr: false`)         |
| `frontend-plugin` | Nuxt layer      | Shared domain types, ports, presentational Vue components      |

**Deps:** `backend → starter` · `other app → client → backend`
**Version catalog:** `gradle/libs.versions.toml`

## 4. Key Commands

### Backend

```bash
./gradlew spotlessApply                                       # Format (Google Java Format)
./gradlew check                                               # compile + test + checkstyle + spotless
./gradlew build                                               # full build
./gradlew :backend:test                                       # unit tests
./gradlew :backend:integrationTest                            # needs Docker
./gradlew generateAllClients                                  # TypeScript + Feign clients
```

### Frontend

```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0                      # always activate nvm first
pnpm install                                                  # from repo root (workspace)
cd frontend && pnpm dev                                       # :3000, local auth
cd frontend && pnpm test                                      # Vitest (100% coverage enforced)
cd frontend && pnpm e2e                                       # Playwright
cd frontend && pnpm lint:fix                                  # Biome auto-fix
```

**Env vars:** `SPRING_BOOT_URL` (default `http://localhost:7070`), `NUXT_PUBLIC_LOCAL_AUTH=true`.

## 5. Authentication

Two modes via `app.keycloak.enabled` in `application.yml`:

- **Local (dev, default):** RSA-signed JWT from `POST /api/public/auth/token`. Issuer = `cbs-nova`.
- **Keycloak (prod):** JWKS from Keycloak realm. Local auth controller disabled.

**Public endpoints:** `/api/public/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`, all `OPTIONS`.
**Stateless:** No HTTP session — every request needs JWT.

## 6. Frontend Architecture

Hexagonal (Ports & Adapters) across two workspaces:

| Layer              | Location                                               | Owns                                             |
|--------------------|--------------------------------------------------------|--------------------------------------------------|
| Domain/Ports       | `frontend-plugin/composables/<feature>/`               | Interfaces, ports, presentational Vue, providers |
| Application        | `frontend/src/app/<feature>/application/`              | Use cases, route configs                         |
| Primary (driving)  | `frontend/src/app/<feature>/infrastructure/primary/`   | Page Vue components                              |
| Secondary (driven) | `frontend/src/app/<feature>/infrastructure/secondary/` | HTTP adapters                                    |
| DI Wiring          | `frontend/src/app/plugins/*.ts`                        | Nuxt plugins connecting adapters → ports         |

**Rule:** `frontend-plugin` must NEVER import from `frontend/`.

**DI (piqure):** `provide` and `inject` must come from the **same** `piqureWrapper(window, 'piqure')` instance —
mixing instances returns `undefined`.

**Styling:** Tailwind CSS v4, Vite plugin. Primary `#D4532D`, secondary `#1A1A2E`.

## 7. Testing Conventions

- Class names: `*Test` (unit), `*IntegrationTest` (integration)
- Method names: `shouldXxxWhenYyy` + `@DisplayName` required
- Use `@MockitoBean` (not deprecated `@MockBean`) — Spring Boot 4.x
- Controllers: `@WebMvcTest` + `@MockitoBean SecurityFilterChain`
- Integration: `@SpringBootTest(RANDOM_PORT)` + `jdbc:tc:postgresql:18.3:///cbsnova`
- ArchUnit in `MainConventions.java`: naming, layering, no Controller→Repository

## 8. Adding a New Feature

7-step hexagonal pattern:

1. `frontend-plugin/composables/<feature>/` — `<Feature>.ts`, `<Feature>Repository.ts`, `<Feature>ListVue.vue`
2. `frontend-plugin/composables/<feature>/<Feature>Provider.ts` — piqure DI key + `provideFor<Feature>()`
3. `frontend/src/app/<feature>/infrastructure/secondary/<Feature>Http.ts` — port impl via `AxiosHttp`
4. `frontend/src/app/<feature>/infrastructure/primary/<Feature>PageVue.vue` — inject + display
5. `frontend/src/app/<feature>/application/<Feature>Router.ts` — route definitions
6. `frontend/src/app/router.ts` — spread `...<feature>Routes()`
7. `frontend/src/app/plugins/<feature>.ts` — Nuxt plugin wiring adapter → port

## 9. Domain Concepts

| Entity            | Role                                                                                   |
|-------------------|----------------------------------------------------------------------------------------|
| **Workflow**      | Temporal-backed state machine. Stateless events get auto-generated stub wrapper.       |
| **Event**         | Triggered operation: `context{}`, `display{}`, `transactions{}`, `finish{}` blocks.    |
| **Transaction**   | Unit of work: `preview()` / `execute()` / `rollback()`. Rollback = compensating entry. |
| **Helper**        | Spring bean or inline DSL (SQL/HTTP). Typed as `HelperFunction<I, O>`.                 |
| **MassOperation** | Batch orchestration. Per-item isolation. Emits `PARTIAL`/`COMPLETED` signals.          |
| **Condition**     | Reusable boolean DSL block across events.                                              |

**DSL:** Java DSL `.java` in `cbs-rules` Gitea repo. Prod: code generation of Temporal workflows/activities; Dev: reflection-based runtime.
**Versioning:** `{semver}-{gitCommitShort}`, instances locked to start version.
**Single API:** `POST /api/events/execute` — callers use `eventNumber`, not workflow IDs.

## 10. Documentation Index

| Document                                                 | Contents                                                            |
|----------------------------------------------------------|---------------------------------------------------------------------|
| [docs/backend-knowledge.md](docs/backend-knowledge.md)   | Structure, security, API, testing, database, internals (8 sub-docs) |
| [docs/frontend-knowledge.md](docs/frontend-knowledge.md) | Architecture, DI, routing, auth, styling, tooling (9 sub-docs)      |
| [docs/tdd.md](docs/tdd.md)                               | Technical design: DSL, execution, API contract, BPMN (25+ sub-docs) |
| [docs/plan.md](docs/plan.md)                             | Implementation plan with task table and phase status                |
