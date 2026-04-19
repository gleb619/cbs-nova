# Backend Internals & Design Decisions

## 1. How Auto-Configuration Works

1. **Registration**: `AutoConfiguration.imports` in the `starter` module points to `NovaAutoConfiguration`.
2. **Scanning**: Spring Boot reads this on startup:
    - `@ComponentScan`: Registers controllers, services, mappers.
    - `@EntityScan`: Registers JPA entities.
    - `@EnableJpaRepositories`: Registers Spring Data repositories.
3. **Consumption**: The `backend` module simply implements the `starter` module; all beans are auto-discovered.

---

## 2. Key Design Decisions

| Decision                        | Rationale                                                  |
|:--------------------------------|:-----------------------------------------------------------|
| **No @EntityScan in backend**   | Delegated to modular starter auto-config.                  |
| **bootJar disabled in starter** | Starter is a library JAR, not a standalone service.        |
| **ddl-auto: validate**          | Enforces Flyway as the source of truth for schema changes. |
| **NoOpPasswordEncoder**         | Simplifies local development/debugging.                    |
| **@MockitoBean**                | Migration to Spring Boot 4.x standards.                    |
| **Scoped ExceptionHandler**     | Modular error handling restricted to starter package.      |
