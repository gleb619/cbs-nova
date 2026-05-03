# Backend Knowledge Base — CBS Nova

This knowledge base covers the modular Spring Boot architecture, security configurations, and development workflows for
the CBS Nova backend.

## Document Directory

1. **[Quick Start Guide](./backend/01-quickstart.md)**
    * Starting PostgreSQL and the Application.
    * Login & JWT acquisition demo.

2. **[Project Structure](./backend/02-structure.md)**
    * Module definitions: `backend`, `starter`, and `client`.
    * Dependency graph and deep dive into modular responsibilities.

3. **[Security & Authentication](./backend/03-security.md)**
    * Local Auth mode vs Keycloak (production) mode.
    * Public endpoints and SecurityConfig breakdown.

4. **[API Specification & Generation](./backend/04-api-spec.md)**
    * Settings API endpoints and error response codes.
    * Swagger UI and client code generation (TypeScript/Feign).

5. **[Development Guide](./backend/05-development.md)**
    * Shared Gradle scripts and version catalog.
    * Essential commands for build, format, and testing.

6. **[Testing Documentation](./backend/06-testing.md)**
    * JUnit 5, Testcontainers, and MockMvc conventions.
    * ArchUnit rules for architecture enforcement.

7. **[Database Documentation](./backend/07-database.md)**
    * PostgreSQL configuration and Flyway migrations.
    * Schema definition and infrastructure setup.

8. **[Internals & Design Decisions](./backend/08-internals.md)**
    * Spring Boot auto-configuration mechanisms.
    * Rationale for key technical choices (e.g., `ddl-auto: validate`).

---

## Technical Stack Summary

- **JDK:** 25
- **Framework:** Spring Boot 4.x
- **Persistence:** PostgreSQL + Hibernate + Flyway
- **Security:** Spring Security + JWT (Keycloak/RSA)
- **API Spec:** OpenAPI 3 / Swagger
- **Versioning:** Gradle + Version Catalog
