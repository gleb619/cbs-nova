# Backend Knowledge — CBS Nova

## Quick Start

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Start the Application

```bash
./gradlew :backend:bootRun
# or
./gradlew :backend:bootJar && java -jar backend/build/libs/backend-0.0.1-SNAPSHOT.jar
```

App starts on **http://localhost:7070**.

### 3. Login & Get JWT Token

```bash
curl -s -X POST http://localhost:7070/api/public/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin1"}' | jq .
```

Response:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 4. Call Settings API

```bash
TOKEN="eyJhbGciOiJSUzI1NiJ9..."

# List all settings
curl -s http://localhost:7070/api/settings \
  -H "Authorization: Bearer $TOKEN" | jq .

# Get by code
curl -s http://localhost:7070/api/settings/code/app.theme \
  -H "Authorization: Bearer $TOKEN" | jq .

# Create a setting
curl -s -X POST http://localhost:7070/api/settings \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"app.theme","value":"dark","description":"Default UI theme"}' | jq .

# Update a setting
curl -s -X PUT http://localhost:7070/api/settings/1 \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"value":"light","description":"Updated theme"}' | jq .

# Delete a setting
curl -s -X DELETE http://localhost:7070/api/settings/1 \
  -H "Authorization: Bearer $TOKEN"
```

### 5. Swagger UI

Open **http://localhost:7070/swagger-ui.html** for interactive API documentation.

---

## Project Structure

```
cbs-nova/
├── backend/                  # Main application (Spring Boot, runnable)
├── starter/                  # Reusable library (Setting CRUD as auto-configured module)
├── client/                   # Generated Feign Java client (publishable library)
├── frontend/                 # Frontend application
├── frontend-plugin/          # Frontend plugin
├── gradle/                   # Shared Gradle scripts + version catalog
│   ├── libs.versions.toml    # Version catalog (centralized dependency management)
│   ├── java.gradle           # Java 25 toolchain, Lombok, MapStruct setup
│   ├── test.gradle           # test + integrationTest source sets, test-logger plugin
│   ├── code-style.gradle     # Spotless (Google Java Format)
│   └── checkstyle.gradle     # Checkstyle configuration
├── docker-compose.yml        # Includes docker/postgresql.yml + docker/keycloak.yml
├── build.gradle              # Root build — version, group, generateAllClients task
└── settings.gradle           # Multi-project includes, PREFER_SETTINGS repo mode
```

### Module Responsibilities

| Module    | Type              | Purpose                                                                                          |
|-----------|-------------------|--------------------------------------------------------------------------------------------------|
| `backend` | Spring Boot app   | Entry point, security config, OpenAPI config, local auth controller                              |
| `starter` | Plain JAR library | Setting CRUD feature — auto-configured via Spring Boot `AutoConfiguration.imports`               |
| `client`  | Plain JAR library | Generated Java Feign client + TypeScript Axios client from OpenAPI spec for integration with app |

### Dependency Graph

```
backend → starter (implementation project(":starter"))
other app → client → backend (implementation project(":client"))
```

---

## Module Deep Dive

### `backend/` — Application Module

| File                                                                                               | Purpose                                                         |
|----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| [`CbsApp.java`](../backend/src/main/java/cbs/app/CbsApp.java)                                      | `@SpringBootApplication` entry point — scans `cbs.app.*`        |
| [`SecurityConfig.java`](../backend/src/main/java/cbs/app/config/SecurityConfig.java)               | Security filter chain, JWT decoders, CORS, local user config    |
| [`LocalAuthController.java`](../backend/src/main/java/cbs/app/controller/LocalAuthController.java) | `POST /api/public/auth/token` — local JWT auth with RSA signing |
| [`OpenApiConfig.java`](../backend/src/main/java/cbs/app/config/OpenApiConfig.java)                 | OpenAPI/Swagger metadata, security schemes (JWT + OAuth2)       |
| [`application.yml`](../backend/src/main/resources/application.yml)                                 | Datasource, JPA, Flyway, logging, app.local-auth users          |

### `starter/` — Reusable Library

| File                                                                                                                                          | Purpose                                                                            |
|-----------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [`NovaAutoConfiguration.java`](../starter/src/main/java/cbs/nova/config/NovaAutoConfiguration.java)                                           | `@AutoConfiguration` + `@ComponentScan` + `@EntityScan` + `@EnableJpaRepositories` |
| [`AutoConfiguration.imports`](../starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | Spring Boot auto-discovery registration                                            |
| [`SettingController.java`](../starter/src/main/java/cbs/nova/controller/SettingController.java)                                               | CRUD endpoints at `/api/settings`                                                  |
| [`AbstractCrudController.java`](../starter/src/main/java/cbs/nova/controller/AbstractCrudController.java)                                     | Generic CRUD interface with OpenAPI annotations                                    |
| [`SettingExceptionHandler.java`](../starter/src/main/java/cbs/nova/controller/SettingExceptionHandler.java)                                   | `@RestControllerAdvice` — converts `EntityNotFoundException` → 404                 |
| [`SettingService.java`](../starter/src/main/java/cbs/nova/service/SettingService.java)                                                        | Business logic — CRUD operations                                                   |
| [`SettingRepository.java`](../starter/src/main/java/cbs/nova/repository/SettingRepository.java)                                               | JPA repository + `findByCode(String)`                                              |
| [`SettingEntity.java`](../starter/src/main/java/cbs/nova/entity/SettingEntity.java)                                                           | JPA entity → `settings` table                                                      |
| [`SettingMapper.java`](../starter/src/main/java/cbs/nova/mapper/SettingMapper.java)                                                           | MapStruct mapper — entity ↔ DTO conversion                                         |
| [`SettingDto.java`](../starter/src/main/java/cbs/nova/model/SettingDto.java)                                                                  | Response DTO with validation                                                       |
| [`SettingCreateDto.java`](../starter/src/main/java/cbs/nova/model/SettingCreateDto.java)                                                      | Create request DTO                                                                 |
| [`SettingUpdateDto.java`](../starter/src/main/java/cbs/nova/model/SettingUpdateDto.java)                                                      | Update request DTO                                                                 |
| [`EntityNotFoundException.java`](../starter/src/main/java/cbs/nova/model/exception/EntityNotFoundException.java)                              | Custom 404 exception                                                               |
| [`V20260410120000__create_settings.sql`](../starter/src/main/resources/db/migration/V20260410120000__create_settings.sql)                     | Flyway migration — creates `settings` table                                        |

### `client/` — Generated API Client

| File                                                               | Purpose                                        |
|--------------------------------------------------------------------|------------------------------------------------|
| [`build.gradle`](../client/build.gradle)                           | OpenAPI generator config for Java Feign client |
| [`.openapi-generator-ignore`](../client/.openapi-generator-ignore) | Ignore patterns for code generation            |

---

## Authentication Flow

### Local Mode (Development)

When `app.keycloak.enabled=false` (default):

1. **RSA Key Pair** — Loaded from classpath resources:
    - Private key: `classpath:local-jwt-pkcs8.pem` (PKCS#8 format)
    - Public key: `classpath:local-jwt-public.pem` (X.509 format)

2. **Token Generation** ([
   `LocalAuthController.java`](../backend/src/main/java/cbs/app/controller/LocalAuthController.java)):
    - Client sends `POST /api/public/auth/token` with `{username, password}`
    - `AuthenticationManager` validates credentials against in-memory users
    - JWT is signed with RSASSA using the private key
    - Response: `{access_token, token_type: "Bearer", expires_in: 3600}`

3. **Token Validation** ([`SecurityConfig.java`](../backend/src/main/java/cbs/app/config/SecurityConfig.java#L75-L85)):
    - `SecurityFilterChain` requires JWT for all non-public endpoints
    - `JwtDecoder` verifies signature with the public key
    - `issuer` claim must match `spring.application.name` ("cbs-nova")

4. **Local Users** (from [`application.yml`](../backend/src/main/resources/application.yml)):
   ```yaml
   app.local-auth:
     users:
       - username: admin1
         password: admin1
         roles: ADMIN,USER
       - username: user1
         password: user1
         roles: USER
   ```

### Keycloak Mode (Production)

When `app.keycloak.enabled=true`:

- `JwtDecoder` is configured via `keycloak.auth-server-url` + `keycloak.realm`
- `NimbusJwtDecoder` fetches JWKS from Keycloak issuer URL
- Local auth controller is disabled (`@ConditionalOnProperty`)

### Public Endpoints

These endpoints do **not** require authentication:

- `/api/public/**` — including `/api/public/auth/token`
- `/actuator/health` — health checks
- `/swagger-ui/**`, `/v3/api-docs/**` — API documentation
- All `OPTIONS` requests — CORS preflight

---

## Security Configuration

[`SecurityConfig.java`](../backend/src/main/java/cbs/app/config/SecurityConfig.java) breakdown:

| Bean                      | Condition                    | Purpose                                                              |
|---------------------------|------------------------------|----------------------------------------------------------------------|
| `securityFilterChain`     | Always                       | Disables CSRF, enables CORS, stateless sessions, JWT resource server |
| `corsConfigurationSource` | Always                       | Allows `http://localhost:3000` with credentials                      |
| `jwtDecoderKeycloak`      | `app.keycloak.enabled=true`  | Fetches JWKS from Keycloak                                           |
| `jwtDecoderLocal`         | `app.keycloak.enabled=false` | Loads RSA public key from classpath                                  |
| `userDetailsService`      | `app.keycloak.enabled=false` | In-memory users from `app.local-auth.users`                          |
| `authenticationManager`   | `app.keycloak.enabled=false` | `DaoAuthenticationProvider` with in-memory UDS                       |
| `passwordEncoder`         | `app.keycloak.enabled=false` | `NoOpPasswordEncoder` (plaintext passwords for local dev)            |

**Key decisions:**

- **Stateless sessions** — no HTTP session, every request requires JWT
- **`@ConditionalOnProperty`** — switch between local/Keycloak via `app.keycloak.enabled`
- **No password encoding in dev** — `NoOpPasswordEncoder` for simplicity

---

## Settings API Walkthrough

### API Endpoints

| Method   | Path                        | Body               | Status | Description             |
|----------|-----------------------------|--------------------|--------|-------------------------|
| `GET`    | `/api/settings`             | —                  | 200    | List all settings       |
| `GET`    | `/api/settings/{id}`        | —                  | 200    | Get by ID               |
| `GET`    | `/api/settings/code/{code}` | —                  | 200    | Get by unique code      |
| `POST`   | `/api/settings`             | `SettingCreateDto` | 201    | Create new setting      |
| `PUT`    | `/api/settings/{id}`        | `SettingUpdateDto` | 200    | Update existing setting |
| `DELETE` | `/api/settings/{id}`        | —                  | 204    | Delete setting          |

### Full curl Demo

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:7070/api/public/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin1"}' | jq -r '.access_token')

# 2. List all settings (empty initially)
curl -s http://localhost:7070/api/settings \
  -H "Authorization: Bearer $TOKEN"

# 3. Create a setting
curl -s -X POST http://localhost:7070/api/settings \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"app.theme","value":"dark","description":"Default UI theme"}'
# Response: {"id":1,"code":"app.theme","value":"dark","description":"Default UI theme"}

# 4. Get by ID
curl -s http://localhost:7070/api/settings/1 \
  -H "Authorization: Bearer $TOKEN"

# 5. Get by code
curl -s http://localhost:7070/api/settings/code/app.theme \
  -H "Authorization: Bearer $TOKEN"

# 6. Update
curl -s -X PUT http://localhost:7070/api/settings/1 \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"value":"light"}'

# 7. Delete
curl -s -X DELETE http://localhost:7070/api/settings/1 \
  -H "Authorization: Bearer $TOKEN"
# Response: 204 No Content
```

### Error Responses

| Status | Condition                                                |
|--------|----------------------------------------------------------|
| 400    | Invalid input (validation failed)                        |
| 401    | Missing or invalid JWT                                   |
| 403    | Insufficient permissions                                 |
| 404    | Setting not found (handled by `SettingExceptionHandler`) |
| 409    | Conflict (e.g., duplicate code on create)                |
| 422    | Validation failed                                        |

---

## Build System

### Version Catalog ([`libs.versions.toml`](../gradle/libs.versions.toml))

Centralized dependency management:

- **Versions** — Java/Kotlin/Spring versions in `[versions]`
- **Libraries** — Typed library references in `[libraries]`
- **Bundles** — Grouped dependencies in `[bundles]` (e.g., `spring-web`, `spring-data`, `spring-security`)
- **Plugins** — Plugin aliases in `[plugins]`

### Gradle Scripts

| File                                               | Purpose                                                                |
|----------------------------------------------------|------------------------------------------------------------------------|
| [`java.gradle`](../gradle/java.gradle)             | Java 25 toolchain, Lombok + MapStruct annotation processing            |
| [`test.gradle`](../gradle/test.gradle)             | `test` + `integrationTest` source sets, test-logger plugin config      |
| [`code-style.gradle`](../gradle/code-style.gradle) | Spotless with Google Java Format, import ordering, trailing whitespace |
| [`checkstyle.gradle`](../gradle/checkstyle.gradle) | Checkstyle with custom rules, relaxed rules for tests                  |

### Key Commands

```bash
# Format code
./gradlew spotlessApply

# Run all checks (compile + test + checkstyle + spotless)
./gradlew check

# Full build
./gradlew build

# Run unit tests
./gradlew test

# Run integration tests (needs Docker)
./gradlew :backend:integrationTest

# Run single test
./gradlew :backend:test --tests "cbs.app.controller.LocalAuthControllerTest"

# Run single test method
./gradlew :backend:test --tests "*shouldReturn200WithTokenWhenValidCredentials"

# Verbose test output
./gradlew test --info
```

---

## Testing

### Test Structure

| Source Set        | Location                                           | Requires                |
|-------------------|----------------------------------------------------|-------------------------|
| `test`            | `backend/src/test/java/`, `starter/src/test/java/` | Nothing (unit tests)    |
| `integrationTest` | `backend/src/integrationTest/java/`                | Docker (Testcontainers) |

### Test Frameworks

| Framework            | Purpose                                                            |
|----------------------|--------------------------------------------------------------------|
| **JUnit 5**          | Test runner, `@Test`, `@DisplayName`                               |
| **Spring Boot Test** | `@WebMvcTest`, `@SpringBootTest`, `@MockitoBean`                   |
| **Testcontainers**   | Auto-provisioned PostgreSQL (`jdbc:tc:postgresql:18.3:///cbsnova`) |
| **ArchUnit**         | Architecture enforcement (naming, layering, dependencies)          |
| **MockMvc**          | HTTP controller testing                                            |
| **AssertJ**          | Fluent assertions                                                  |

### Test Conventions

1. **Class naming**: `*Test` (unit), `*IntegrationTest` (integration)
2. **Method naming**: `shouldXxxWhenYyy` (e.g., `shouldReturn200WithTokenWhenValidCredentials`)
3. **Every test method must have `@DisplayName`**
4. **Use `@MockitoBean`** (Spring Boot 4.x) — NOT `@MockBean`
5. **`@WebMvcTest`** for controllers — mock security with `@MockitoBean SecurityFilterChain`
6. **`@SpringBootTest(webEnvironment = RANDOM_PORT)`** for integration tests

### Architecture Tests (ArchUnit)

ArchUnit rules enforce:

- **Naming**: `*Controller`, `*Service`, `*Repository`, `*Entity`, `*Mapper`, `*Dto`
- **Package constraints**: `@RestController` → `controller..`, `@Service` → `service..`
- **Layer dependencies**: Controller → Service → Repository → Entity (no circular deps)
- **Guards**: Controllers must not access Repositories directly

Config: `backend/src/test/resources/archunit.properties`

---

## OpenAPI & Code Generation

### Swagger UI

- **URL**: http://localhost:7070/swagger-ui.html
- **Spec**: http://localhost:7070/v3/api-docs
- Configured in [`OpenApiConfig.java`](../backend/src/main/java/cbs/app/config/OpenApiConfig.java)

### TypeScript Client Generation

```bash
# Option 1: Fetch from running app
./gradlew :starter:generateTsClient

# Option 2: Export from integration test first
./gradlew :backend:exportOpenApi
./gradlew :starter:generateTsClient
```

Output: `starter/generated-ts/` (api/ + models/)

### Java Feign Client Generation

```bash
./gradlew :client:generateFeignClient

# Publish locally for testing
./gradlew :client:publishToMavenLocal
```

### Generate All Clients

```bash
./gradlew generateAllClients
```

### Export OpenAPI Spec

```bash
./gradlew :backend:exportOpenApi
# Outputs to: backend/build/openapi.json
```

---

## Database

### Configuration

From [`application.yml`](../backend/src/main/resources/application.yml):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cbsnova
    username: cbsnova
    password: cbsnova
  jpa:
    hibernate:
      ddl-auto: validate  # Schema validated against Flyway, not auto-generated
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### Flyway Migrations

| Migration                              | Location                                   | Purpose                  |
|----------------------------------------|--------------------------------------------|--------------------------|
| `V20260101101010__init.sql`            | `starter/src/main/resources/db/migration/` | Initial schema (if any)  |
| `V20260410120000__create_settings.sql` | `starter/src/main/resources/db/migration/` | Creates `settings` table |

### Schema

```sql
CREATE TABLE settings (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    value       VARCHAR(255) NOT NULL,
    description VARCHAR(255)
);
```

### Docker Compose

[`docker-compose.yml`](../docker-compose.yml) includes:

- `docker/postgresql.yml` — PostgreSQL database
- `docker/keycloak.yml` — Keycloak identity provider (optional)

---

## Tools & When to Use Them

| Task                          | Tool                           | Command / Annotation                                                    |
|-------------------------------|--------------------------------|-------------------------------------------------------------------------|
| **API testing**               | curl                           | `curl -H 'Authorization: Bearer <token>' http://localhost:7070/api/...` |
| **API exploration**           | Swagger UI                     | http://localhost:7070/swagger-ui.html                                   |
| **Unit test controllers**     | `@WebMvcTest`                  | `@WebMvcTest(MyController.class)` + `@MockitoBean SecurityFilterChain`  |
| **Integration test full app** | `@SpringBootTest(RANDOM_PORT)` | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers        |
| **Database tests**            | Testcontainers                 | `jdbc:tc:postgresql:18.3:///cbsnova`                                    |
| **Architecture checks**       | ArchUnit                       | `MainConventions.java` — naming, layering, dependencies                 |
| **Code formatting**           | Spotless                       | `./gradlew spotlessApply` (Google Java Format)                          |
| **Style checks**              | Checkstyle                     | `./gradlew checkstyleMain`                                              |
| **Client generation**         | OpenAPI Generator              | `./gradlew generateAllClients`                                          |
| **Build verification**        | Gradle                         | `./gradlew check` or `./gradlew build`                                  |

### Test Output Tips

```bash
# Default: quiet (only failures)
./gradlew test

# Show all test names and timing
./gradlew test --info

# Show stdout from tests
./gradlew test --info 2>&1 | grep "Standard output"

# Show slow tests (>2s threshold)
./gradlew test --info  # test-logger logs slow tests
```

---

## How Auto-Configuration Works

1. **Registration**: [
   `AutoConfiguration.imports`](../starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
   contains:
   ```
   cbs.nova.config.NovaAutoConfiguration
   ```

2. **Processing**: Spring Boot reads this file on startup and processes `NovaAutoConfiguration`:
    - `@ComponentScan(basePackages = {"cbs.nova.service", "cbs.nova.controller", "cbs.nova.mapper"})` — registers
      Service, Controller, Mapper beans
    - `@EntityScan(basePackages = "cbs.nova.entity")` — registers JPA entities
    - `@EnableJpaRepositories(basePackages = "cbs.nova.repository")` — registers Spring Data repositories

3. **Consumption**: `backend/build.gradle` has `implementation project(":starter")` — no extra config needed. The
   starter's JAR is on the classpath, so auto-configuration is discovered automatically.

4. **Migration**: Flyway picks up migrations from `classpath:db/migration` — includes paths from both `backend/` and
   `starter/` JARs.

---

## Key Decisions

| Decision                                      | Rationale                                                        |
|-----------------------------------------------|------------------------------------------------------------------|
| **No `@EntityScan` in backend**               | Entities auto-detected via starter's `@EntityScan`               |
| **`bootJar { enabled = false }` in starter**  | Starter is a library, not a runnable app                         |
| **`@RestControllerAdvice` scoped to starter** | Won't interfere with backend's own exception handlers            |
| **MapStruct ignores `id` in `toEntity()`**    | Prevents client-side ID injection on create                      |
| **`NoOpPasswordEncoder` for local dev**       | Simplifies development — plaintext passwords in config           |
| **`ddl-auto: validate`**                      | Schema validated against Flyway, not auto-generated by Hibernate |
| **`@MockitoBean` not `@MockBean`**            | Spring Boot 4.x migration — `@MockBean` is deprecated            |
