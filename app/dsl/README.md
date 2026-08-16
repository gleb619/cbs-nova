# app-dsl

Standalone customer-style DSL module for the cbs-nova platform.

This project mirrors what an external team would host in their own repository: it applies the
published `cbs.nova.dsl` Gradle plugin, authors compact DSL sources, and produces a jar that a
Spring Boot consumer can depend on.

## Prerequisites

The cbs-nova backend artifacts must be published to your local Maven repository:

```bash
cd ../backend
./gradlew publishToMavenLocal
```

## Build

```bash
./gradlew build
```

## Publish to Maven Local

```bash
./gradlew publishToMavenLocal
```

This publishes `cbs.nova:app-dsl:0.0.1-SNAPSHOT` so that `app/server` (or any other consumer) can
resolve it.

## Source layout

- `src/dsl/` — compact DSL sources (no `class`, no `public`, no package statement).
- `src/models/` — compact `@Json` record sources used by the DSL flows.

Both folders are compiled into the package configured by `dslCompile.dslPackage`
(`cbs.nova.app.dsl`).

## Generated output

The `compileDsl` task writes generated sources and classes to `build/generated/`, including:

- Temporal workflow interfaces and definitions for each Process.
- Temporal activity interfaces and definitions for each Transaction.
- `META-INF/services/cbs.nova.dsl.DslDefinitionProvider` for runtime discovery.

## Build notes

- The plugin is configured with `runtimeModule = ''` so the DSL compiler classpath resolves only
  `dsl-api`, `dsl`, and `dsl-codegen` from Maven Local, avoiding a transitive pull of the full
  Spring Boot runtime through `starter`.
- Generated classes are copied into the main compile output by `copyGeneratedClasses` so they are
  packaged in the published jar.
