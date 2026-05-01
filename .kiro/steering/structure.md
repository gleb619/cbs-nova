# Project Structure — CBS Nova

## Root Layout

```
cbs-nova/
├── backend/          # Spring Boot application (Java 25 + Java dsl, port :7070)
├── starter/          # Reusable library — Setting CRUD, auto-configured via Spring Boot
├── client/           # Generated Java Feign client (publishable library)
├── frontend/         # Nuxt 3 SPA + BFF (port :3000)
├── frontend-plugin/  # Shared Nuxt layer (@cbs/admin-plugin) — domain, ports, components
├── gradle/           # Shared Gradle scripts + version catalog (libs.versions.toml)
├── docker/           # Docker Compose fragments (postgresql.yml, keycloak.yml)
├── docs/             # Technical Design Documents and knowledge bases
├── build.gradle      # Root build — group, version, generateAllClients task
└── settings.gradle   # Multi-project includes
```

## Module Responsibilities

| Module            | Type            | Purpose                                                                |
|-------------------|-----------------|------------------------------------------------------------------------|
| `backend`         | Spring Boot app | Entry point, security config, OpenAPI config, local auth controller    |
| `starter`         | Library JAR     | Setting CRUD feature — auto-configured via `AutoConfiguration.imports` |
| `client`          | Library JAR     | Generated Java Feign + TypeScript Axios clients from OpenAPI spec      |
| `frontend`        | Nuxt 3 SPA      | Pages, HTTP adapters, route definitions, DI wiring (Nuxt plugins)      |
| `frontend-plugin` | Nuxt layer      | Domain types, port interfaces, presentational Vue components, DI setup |

Dependency graph: `backend → starter`, `other-app → client → backend`

## Backend Structure (`backend/` + `starter/`)

```
backend/src/main/java/cbs/app/
├── CbsApp.java                        # @SpringBootApplication entry point
├── config/
│   ├── SecurityConfig.java            # JWT, CORS, filter chain
│   └── OpenApiConfig.java             # Swagger metadata
└── controller/
    └── LocalAuthController.java       # POST /api/public/auth/token

starter/src/main/java/cbs/nova/
├── config/NovaAutoConfiguration.java  # @AutoConfiguration — scans service/controller/mapper
├── controller/
│   ├── AbstractCrudController.java    # Generic CRUD interface
│   ├── SettingController.java         # /api/settings endpoints
│   └── SettingExceptionHandler.java   # @RestControllerAdvice → 404
├── service/SettingService.java
├── repository/SettingRepository.java
├── entity/SettingEntity.java
├── mapper/SettingMapper.java          # MapStruct entity ↔ DTO
└── model/                             # DTOs: SettingDto, SettingCreateDto, SettingUpdateDto
```

Naming conventions enforced by ArchUnit: `*Controller`, `*Service`, `*Repository`, `*Entity`, `*Mapper`, `*Dto`

Layer dependency rule: Controller → Service → Repository → Entity (no skipping, no circular deps)

## Frontend Structure

The frontend follows **hexagonal (ports and adapters) architecture** split across two packages:

- `frontend-plugin/` owns: domain types, port interfaces, presentational components, DI provider setup
- `frontend/` owns: HTTP adapters, page components, route definitions, DI wiring via Nuxt plugins

**`frontend-plugin/` must never import from `frontend/`.**

```
frontend-plugin/composables/<feature>/
├── <Entity>.ts              # Domain type / interface
├── <Entity>Repository.ts    # Port interface
├── <Entity>Provider.ts      # piqure DI: key, provideFor*, inject
└── <Entity>ListVue.vue      # Presentational component

frontend/src/app/<feature>/
├── application/
│   └── <Feature>Router.ts   # Route definitions
└── infrastructure/
    ├── primary/
    │   └── <Feature>PageVue.vue   # Page component (injects repository)
    └── secondary/
        └── <Entity>Http.ts        # HTTP adapter implementing the port

frontend/src/app/plugins/
└── <feature>.ts             # Nuxt plugin: wires HTTP adapter into piqure DI
```

### DI Pattern (piqure)

```typescript
// 1. Provider (frontend-plugin) — defines key + provide/inject from same piqureWrapper
export const SETTING_REPOSITORY = key<SettingRepository>('SettingRepository');
export const provideForSetting = (repo: SettingRepository) => provide(SETTING_REPOSITORY, repo);

// 2. Nuxt plugin (frontend) — wires adapter at startup
provideForSetting(new SettingHttp(new AxiosHttp(axios.create({ baseURL }))));

// 3. Consumer (page component) — injects the implementation
const repo = inject(SETTING_REPOSITORY);
```

Critical: `provide` and `inject` must come from the **same** `piqureWrapper` instance.

## Test Structure

### Backend

| Source Set        | Location                                 | Requires       |
|-------------------|------------------------------------------|----------------|
| `test`            | `backend/src/test/`, `starter/src/test/` | Nothing (unit) |
| `integrationTest` | `backend/src/integrationTest/`           | Docker         |

### Frontend

| Type | Location                         | Runner     |
|------|----------------------------------|------------|
| Unit | `frontend/src/unit/**/*.spec.ts` | Vitest     |
| Unit | `frontend-plugin/` (colocated)   | Vitest     |
| E2E  | `frontend/src/e2e/`              | Playwright |

## Database Migrations

Flyway migrations live in `starter/src/main/resources/db/migration/`.
Naming: `V{timestamp}__{description}.sql` (e.g., `V20260410120000__create_settings.sql`)
`ddl-auto: validate` — schema is managed by Flyway only, never auto-generated by Hibernate.

## Adding a New Feature (Checklist)

1. Domain type + port interface → `frontend-plugin/composables/<feature>/`
2. DI provider → `frontend-plugin/composables/<feature>/<Entity>Provider.ts`
3. Presentational component → `frontend-plugin/composables/<feature>/<Entity>ListVue.vue`
4. HTTP adapter → `frontend/src/app/<feature>/infrastructure/secondary/<Entity>Http.ts`
5. Page component → `frontend/src/app/<feature>/infrastructure/primary/<Feature>PageVue.vue`
6. Router → `frontend/src/app/<feature>/application/<Feature>Router.ts`
7. Register routes → `frontend/src/app/router.ts`
8. Nuxt plugin → `frontend/src/app/plugins/<feature>.ts`
