# 0005. Run the backend platform on Spring Boot 4, Jackson 3, and Java 25

- **Status:** Accepted
- **Date:** 2026-09-13 (retroactive — records an existing decision, not a forward-looking proposal)

## Context

The backend pins its platform generation in `backend/gradle/libs.versions.toml`:

- `spring-boot = "4.0.4"`
- `spring-cloud = "2025.1.1"`
- `springdoc-openapi = "3.0.2"`
- `jackson-databind3 = { module = "tools.jackson.core:jackson-databind", version = "3.1.0" }`
- `java = "25"`

The repo-wide Java toolchain (`backend/gradle/java.gradle`) uses
`JavaLanguageVersion.of(libs.versions.java.get())`, so the compile floor is Java 25.

Spring Boot 4 is built on Spring Framework 7, and that generation is what pulls the Jackson
runtime to the 3.x line (`tools.jackson.*`) and the springdoc starter to its 3.x major version.
`spring-boot-starter-web` brings in `spring-boot-starter-jackson` / `spring-boot-jackson:4.0.4`,
which resolves `tools.jackson.core:jackson-databind:3.1.0`. The starter module additionally
declares the explicit dependency `libs.jackson.databind3` in
`backend/dsl-starter/starter/build.gradle`, so the Jackson 3 pin is intentional, not accidental.

The move is already pervasive in source: 111 Java files under `backend/` import `tools.jackson.*`.
The split package is the first thing a new contributor hits.

## Decision

We will track the current Spring generation (Spring Boot 4 / Spring Framework 7 / Jackson 3) as
the single backend baseline, rather than pinning to the previous LTS (Spring Boot 3 / Jackson 2)
and deferring the migration.

No written rationale for the original baseline choice was found in `docs/`, `docs/ideas/`, or the
codebase, so the reasons below are **inferred**:

- One modern baseline avoids paying interest on two deferred migrations (Spring Boot 3→4 and
  Jackson 2→3) later.
- The compact JEP-512 DSL authoring already needs a recent JDK, so Java 25 is required for the
  build anyway; running the runtime on the same generation removes compile/runtime version
  juggling.
- A single upgrade now is cheaper than a second, forced migration later when older versions lose
  support or block other dependencies.

Version bumps are deliberate and pinned in `libs.versions.toml`; pre-GA / early-GA ecosystem risk
is accepted.

## Mechanism

**Jackson runtime vs. annotations split.** The runtime types live in `tools.jackson.*`
(`tools.jackson.databind.ObjectMapper`, `tools.jackson.databind.JsonNode`,
`tools.jackson.databind.json.JsonMapper`, etc.). The annotations stayed in
`com.fasterxml.jackson.annotation.*` — `@JsonInclude`, `@JsonProperty`, and the rest are still in
the `com.fasterxml.jackson.annotation` package. This is the #1 gotcha when reading or writing
DTOs: the body of the class uses one Jackson family while the field annotations use the other.

A concrete example is the error-envelope record `cbs.nova.starter.model.ErrorEntry`:

```java
package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ErrorEntry(
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) String stackTrace) {
}
```

The same pattern appears throughout `ExecutionDto`, `VcsModels`, `CompileDiagnostic`, and other
response records: the annotations are still `com.fasterxml.jackson.annotation.*` even though the
serializer is Jackson 3.

**ObjectMapper construction.** In Jackson 3 `ObjectMapper` is effectively immutable; production
code builds standalone mappers with `JsonMapper.builder().build()`. Examples in the starter:
`TemporalConfiguration.temporalDslProcessLauncher(...)`, `DslDefinitionBundleService`,
`InputValidator`, `PreviewCacheKeyBuilder`, and the platform-side `JacksonJsonSchemaGenerator`.

The shared application `ObjectMapper` is not declared by the starter itself; it is produced by
Spring Boot's `JacksonAutoConfiguration` (pulled in via `spring-boot-starter-jackson` on the web
starter classpath). No custom `@Bean ObjectMapper` exists in the starter source.

**Build wrapper caveat.** Root `./gradlew` is Gradle 8.13 and fails under Java 25. Use
`backend/dsl-platform/gradlew` (Gradle 9.4.1) for platform and starter builds. This is already
documented in the top-level [CLAUDE.md](../CLAUDE.md); this ADR references it rather than
duplicating it.

**Coexisting Jackson 2.x.** A `com.fasterxml.jackson.core:jackson-databind:2.21.1` line still
resolves on the classpath, pulled transitively by `io.temporal:temporal-sdk:1.27.0`,
`org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2`, and
`org.springframework.boot:spring-boot-flyway:4.0.4` (via `org.flywaydb:flyway-core:11.14.1`). It
coexists with `tools.jackson.core:jackson-databind:3.1.0` because the two families have different
Maven coordinates and different Java packages. Spring Boot 4's `spring-boot-jackson` BOM governs
the 3.x line, while Gradle's resolution / platform constraint keeps the 2.x line at 2.21.1 for the
third-party libraries that still require it. No classpath conflicts were observed in the
`:starter:dependencyInsight --dependency jackson-databind` output.

## Consequences

**Positive**

- One modern baseline removes deferred migration debt for Spring Boot, Jackson, and the JDK.
- Jackson 3's immutable `ObjectMapper` makes accidental mutation harder once the code is
  converted to the builder style.
- Staying on the current Spring generation means security patches and feature releases arrive
  through the normal patch cadence, not through a high-risk major-version leap later.

**Negative**

- **Upgrade cadence cost.** Spring Boot 4 is early in its lifecycle, so patch releases are
  frequent and ecosystem recipes are still sparse.
- **Smaller example ecosystem.** Stack Overflow, blog posts, and third-party samples for
  `tools.jackson` and Spring Boot 4 / Spring Framework 7 are scarcer than for the Jackson 2 /
  Spring Boot 3 generation.
- **`tools.jackson` learning curve.** The package split (runtime in `tools.jackson.*`,
  annotations in `com.fasterxml.jackson.annotation.*`) is non-obvious and will trip new
  contributors until it becomes muscle memory.
- **Two Jackson families on the classpath.** Debugging serialization issues requires checking
  which `ObjectMapper` implementation a component actually holds.

**Neutral**

- This ADR is retroactive and records the existing state; it changes no dependency pins or code.
- The baseline decision is orthogonal to preview/run/explain modes, the helper SPI, and the BFF;
  it underpins all of them.

## Cross-links

- [`docs/architecture-backend.md`](../architecture-backend.md) — high-level backend design; see
  ADR 0005 for the platform baseline.
- [Top-level `CLAUDE.md`](../CLAUDE.md) — Java 25 / Gradle wrapper caveat.
- `backend/gradle/libs.versions.toml` — the pinned versions above.
- `backend/gradle/java.gradle` — Java 25 toolchain definition.
- `backend/dsl-starter/starter/build.gradle` — explicit `libs.jackson.databind3` dependency.
- `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/model/ErrorEntry.java` — example of
  the `com.fasterxml.jackson.annotation.JsonInclude(NON_NULL)` pattern in a Jackson 3 runtime.
