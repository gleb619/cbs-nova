# Backend Project Structure

## 1. Directory Layout

```
cbs-nova/
├── backend/                  # Main application (Spring Boot, runnable)
├── starter/                  # Reusable library (Setting CRUD as auto-configured module)
├── client/                   # Generated Feign Java client (publishable library)
├── gradle/                   # Shared Gradle scripts + version catalog
│   ├── libs.versions.toml    # Version catalog
│   ├── java.gradle           # Java 25 setup
│   ├── test.gradle           # Test sets
│   └── code-style.gradle     # Spotless/Checkstyle
├── docker-compose.yml        # Infrastructure
└── settings.gradle           # Module definitions
```

## 2. Module Responsibilities

| Module    | Type              | Purpose                                                                |
|-----------|-------------------|------------------------------------------------------------------------|
| `backend` | Spring Boot app   | Entry point, security, OpenAPI, local auth                             |
| `starter` | Plain JAR library | Setting CRUD feature — auto-configured via `AutoConfiguration.imports` |
| `client`  | Plain JAR library | Generated Feign + TypeScript clients from OpenAPI spec                 |

## 3. Module Deep Dive

### `backend/` — Application Module

- `CbsApp.java`: Main entry point.
- `SecurityConfig.java`: Security filter chain and JWT setup.
- `application.yml`: Configuration for datasource and auth.

### `starter/` — Reusable Library

- `NovaAutoConfiguration.java`: Auto-configuration wiring.
- `SettingController.java`: CRUD endpoints.
- `SettingService.java`: Business logic.
- `V20260410120000__create_settings.sql`: Flyway schema migration.

### `client/` — Generated API Client

- config for OpenAPI generator.
