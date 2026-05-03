# Backend Development Guide

## 1. Build System

### Version Catalog

Dependency management is centralized in `gradle/libs.versions.toml`.

### Shared Gradle Scripts

- `java.gradle`: Java 25 toolchain, Lombok, MapStruct.
- `test.gradle`: Configures standard and integration test sets.
- `code-style.gradle`: Spotless with Google Java Format.
- `checkstyle.gradle`: Static analysis rules.

---

## 2. Essential Commands

| Command                              | Purpose                              |
|:-------------------------------------|:-------------------------------------|
| `./gradlew spotlessApply`            | Format code automatically            |
| `./gradlew check`                    | Run all checks (tests, style, lint)  |
| `./gradlew build`                    | Full assembly                        |
| `./gradlew test`                     | Run unit tests                       |
| `./gradlew :backend:integrationTest` | Run integration tests (needs Docker) |

---

## 3. Tools & Tips

| Task                | Tool       | Command / Annotation                          |
|:--------------------|:-----------|:----------------------------------------------|
| **API Testing**     | curl       | `curl -H 'Authorization: Bearer <token>' ...` |
| **API Exploration** | Swagger UI | http://localhost:7070/swagger-ui.html         |
| **Architecture**    | ArchUnit   | `MainConventions.java`                        |
| **Code Formatting** | Spotless   | Google Java Format                            |

### Test Output Tips

- `./gradlew test --info`: Show all test names and timing.
- `./gradlew test --info 2>&1 | grep "Standard output"`: Show stdout from tests.
