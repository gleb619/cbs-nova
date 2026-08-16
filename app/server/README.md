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
