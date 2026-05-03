# Backend Testing Documentation

## 1. Test Structure

| Source Set        | Location                    | Purpose                                  |
|:------------------|:----------------------------|:-----------------------------------------|
| `test`            | `src/test/java/`            | Unit tests (no external deps)            |
| `integrationTest` | `src/integrationTest/java/` | Functional tests (Docker/Testcontainers) |

## 2. Test Frameworks

- **JUnit 5**: Runner and assertions.
- **Spring Boot Test**: `@WebMvcTest` (slice), `@SpringBootTest` (full).
- **Testcontainers**: Provisioned PostgreSQL for integration tests.
- **MockMvc**: Mocking web layer interactions.
- **AssertJ**: Fluent assertions.

## 3. Conventions

- **Class Naming**: `*Test` for unit, `*IntegrationTest` for integration.
- **Method Naming**: `shouldXxxWhenYyy` (e.g., `shouldReturn200WhenValidCredentials`).
- **Annotations**:
    - `@DisplayName` is required on every test method.
    - Use `@MockitoBean` (not `@MockBean`).
    - `@WebMvcTest` for controllers; mock security with `SecurityFilterChain`.

## 4. Architecture Verification (ArchUnit)

ArchUnit rules in `MainConventions.java` enforce:

- **Layering**: Controller → Service → Repository → Entity (no circular deps).
- **Naming**: Strict suffix checking (`*Controller`, `*Service`).
- **Isolation**: Controllers must not access Repositories directly.
