# Backend Knowledge

## Stack

- **JDK:** 25 · **Framework:** Spring Boot 4.x · **DB:** PostgreSQL + Flyway
- **Security:** JWT (RSA local dev / Keycloak prod) · **API:** OpenAPI 3 + Swagger UI

## Modules

| Module    | Type            | Purpose                                                        |
|-----------|-----------------|----------------------------------------------------------------|
| `backend` | Spring Boot app | Entry point, security, OpenAPI, local auth                     |
| `starter` | Library JAR     | Settings CRUD, auto-configured via `AutoConfiguration.imports` |
| `client`  | Library JAR     | Generated Feign + TypeScript clients from OpenAPI spec         |

## Quick Start

```bash
docker compose up -d                    # PostgreSQL
./gradlew :backend:bootRun              # :7070

# Get JWT (admin1/admin1 or user1/user1)
curl -X POST http://localhost:7070/api/public/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin1"}'
```

Swagger UI: http://localhost:7070/swagger-ui.html

## Authentication

| Mode                | Config                       | Details                                    |
|---------------------|------------------------------|--------------------------------------------|
| **Local** (dev)     | `app.keycloak.enabled=false` | RSA-signed JWT, users in `application.yml` |
| **Keycloak** (prod) | `app.keycloak.enabled=true`  | JWKS from Keycloak, local auth disabled    |

**Public endpoints:** `/api/public/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`, all `OPTIONS`

## API

**Settings CRUD:** `GET /api/settings`, `GET /api/settings/{id}`, `GET /api/settings/code/{code}`, `POST /api/settings`, `PUT /api/settings/{id}`, `DELETE /api/settings/{id}`

**Generate clients:** `./gradlew generateAllClients` (TypeScript → `starter/generated-ts/`, Feign → `client/`)

## Dev Commands

| Command                              | Purpose                            |
|--------------------------------------|------------------------------------|
| `./gradlew spotlessApply`            | Format code (Google Java Format)   |
| `./gradlew check`                    | All checks: compile + test + style |
| `./gradlew build`                    | Full assembly                      |
| `./gradlew :backend:integrationTest` | Integration tests (needs Docker)   |

## Testing

- **Naming:** `*Test` (unit), `*IntegrationTest` (integration)
- **Methods:** `shouldXxxWhenYyy` + `@DisplayName` required
- **Mocking:** `@MockitoBean` (not `@MockBean`) — Spring Boot 4.x
- **Controllers:** `@WebMvcTest` + `@MockitoBean SecurityFilterChain`
- **Integration:** `@SpringBootTest(RANDOM_PORT)` + `jdbc:tc:postgresql:18.3:///cbsnova`

**ArchUnit rules** in `MainConventions.java`: Controller→Service→Repository layering, naming enforcement, no direct Controller→Repository access.

## Database

- **DDL:** `ddl-auto: validate` (schema validated against Flyway migrations)
- **Migrations:** `starter/src/main/resources/db/migration/`
- **Integration:** Testcontainers `jdbc:tc:postgresql:18.3:///cbsnova`

## Design Decisions

| Decision                                           | Rationale                                    |
|----------------------------------------------------|----------------------------------------------|
| No `@EntityScan` in backend                        | Delegated to modular starter auto-config     |
| `bootJar` disabled in starter                      | Starter is a library, not standalone service |
| `ddl-auto: validate`                               | Enforces Flyway as source of truth           |
| `NoOpPasswordEncoder`                              | Simplifies local development                 |
| `@MockitoBean`                                     | Spring Boot 4.x migration                    |
| Auto-configuration via `AutoConfiguration.imports` | Clean module separation                      |
