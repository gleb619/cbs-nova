# cbs-nova-server

Standalone Spring Boot application that consumes the cbs-nova starter from Maven Local.

## Prerequisites

The backend modules must be published to your local Maven repository:

```bash
cd backend
./gradlew publishToMavenLocal
```

## Build

```bash
cd app/server
./gradlew build
```

## Run

```bash
./gradlew bootRun
```

The application starts on port `8090`. Actuator endpoints are exposed at `/actuator`.

## DSL integration

This server consumes the standalone DSL module published from `app/dsl/`.

### Full build workflow

```bash
cd backend
./gradlew publishToMavenLocal

cd ../app/dsl
./gradlew publishToMavenLocal

cd ../app/server
./gradlew build
```

The `DslRuntimeModesTest` integration test verifies that `preview`, `explain`, and `run` all work
for the `OrderProcess` flow defined in `app-dsl`.

### Runtime discovery

`app-dsl` ships SPI metadata (`META-INF/services/cbs.nova.dsl.DslDefinitionProvider`) so the server
picks up `OrderProcess` and `VALIDATE_ORDER` from the classpath. `DslDefinitionLoaderConfig` is an
`ApplicationRunner` that loads those definitions into `GlobalManager` on startup.
