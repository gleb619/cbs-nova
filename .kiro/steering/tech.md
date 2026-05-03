# Tech Stack — CBS Nova

## Backend

| Layer       | Technology                                                           |
|-------------|----------------------------------------------------------------------|
| Language    | Java 25 + Kotlin (mixed)                                             |
| Framework   | Spring Boot 4.x                                                      |
| Workflows   | Temporal                                                             |
| Persistence | PostgreSQL + Spring Data JPA + Flyway                                |
| Security    | Spring Security, OAuth2 Resource Server, JWT (local RSA or Keycloak) |
| Validation  | Avaje Validator                                                      |
| Mapping     | MapStruct                                                            |
| Boilerplate | Lombok + JSpecify                                                    |
| API Docs    | SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`)                 |
| HTTP Client | Spring Cloud OpenFeign                                               |

## Frontend

| Layer       | Technology                                 |
|-------------|--------------------------------------------|
| Framework   | Nuxt 3.15+ (SPA mode, `ssr: false`)        |
| UI          | Vue 3.5, Vue Router 5, Tailwind CSS v4     |
| State       | Pinia 3 + pinia-plugin-persistedstate      |
| DI          | piqure 2.2 (provide/inject pattern)        |
| HTTP        | Axios 1.14 wrapped in `AxiosHttp`          |
| i18n        | i18next 25 + i18next-vue (EN + RU)         |
| Auth        | Local Auth (dev) or Keycloak (prod)        |
| Lint/Format | Biome 1.9.4 (replaces ESLint + Prettier)   |
| Unit Tests  | Vitest 4 + @vue/test-utils + jsdom         |
| E2E Tests   | Playwright 1.59                            |
| Build       | vue-tsc 3 + Vite 7, orchestrated by Gradle |

## Build System

Gradle multi-module build with a version catalog at `gradle/libs.versions.toml`.

Shared Gradle scripts in `gradle/`:

- `java.gradle` — Java 25 toolchain, Lombok, MapStruct annotation processing
- `test.gradle` — `test` + `integrationTest` source sets, test-logger plugin
- `code-style.gradle` — Spotless with Google Java Format
- `checkstyle.gradle` — Checkstyle with custom rules

Frontend uses pnpm workspaces (`pnpm-workspace.yaml`) with packages `frontend/` and `frontend-plugin/`.

## Common Commands

### Infrastructure

```bash
docker compose up -d          # Start PostgreSQL + Keycloak
```

### Backend

```bash
./gradlew :backend:bootRun                    # Start backend on :7070
./gradlew build                               # Full build
./gradlew check                               # Compile + test + checkstyle + spotless
./gradlew test                                # Unit tests
./gradlew :backend:integrationTest            # Integration tests (requires Docker)
./gradlew spotlessApply                       # Auto-format Java/Kotlin code
./gradlew :backend:test --tests "*MyTest"     # Run single test class or method
```

### Frontend

```bash
# From repo root
pnpm install                          # Install all workspace deps
./gradlew :frontend:assemble          # Full frontend build via Gradle
./gradlew :frontend:lint              # Biome lint via Gradle

# From frontend/
pnpm dev                              # Dev server with Local Auth on :3000
pnpm serve                            # Dev server with Keycloak on :3000
pnpm build                            # vue-tsc + nuxt build
pnpm test                             # Vitest unit tests
pnpm test:coverage                    # With coverage
pnpm lint                             # Biome check
pnpm lint:fix                         # Biome auto-fix
pnpm e2e                              # Playwright UI
pnpm e2e:headless                     # Headless E2E (CI)
```

### Code Generation

```bash
./gradlew generateAllClients          # Generate TS + Java Feign clients
./gradlew :backend:exportOpenApi      # Export OpenAPI spec to backend/build/openapi.json
./gradlew :starter:generateTsClient  # Generate TypeScript Axios client
./gradlew :client:generateFeignClient # Generate Java Feign client
```

## Environment Variables

| Variable                 | Default                 | Effect                                |
|--------------------------|-------------------------|---------------------------------------|
| `SPRING_BOOT_URL`        | `http://localhost:7070` | Backend URL for axios + devProxy      |
| `NUXT_PUBLIC_LOCAL_AUTH` | `false`                 | Enable Local Auth mode when `true`    |
| `app.keycloak.enabled`   | `false`                 | Switch between local JWT and Keycloak |

## Code Style Rules

### Java/Kotlin

- Google Java Format (enforced by Spotless — run `./gradlew spotlessApply`)
- Checkstyle with custom rules in `gradle/checkstyle/`
- `@MockitoBean` (NOT `@MockBean` — deprecated in Spring Boot 4.x)
- Test method naming: `shouldXxxWhenYyy`
- Every test method must have `@DisplayName`

### TypeScript/Vue

- Biome 1.9.4: 2-space indent, single quotes, always semicolons, line width 140
- `noUnusedImports: error`, `useImportType: error`
- `noExplicitAny: off`
