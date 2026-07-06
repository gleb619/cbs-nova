# T94 — Actuator runtime enablement

**Tier:** backend
**Status:** Backlog
**Owner:** loop

## Goal

`spring-boot-starter-actuator` is currently `compileOnly` (plus `testImplementation`) in `starter/build.gradle`. A custom `DslHealthIndicator` (and its test) already exists, but because actuator is not on the runtime classpath the `/actuator/health` endpoint is never actually served — the observability surface is half-wired. Promote actuator to the runtime classpath and expose health, info, and metrics so deployments get a live ops surface.

## Acceptance criteria

- [ ] `spring-boot-starter-starter-actuator` (or `spring-boot-starter-actuator`) moved from `compileOnly` to `implementation` in `backend/starter/build.gradle`.
- [ ] `application.yml` / `application.properties` configures exposure:
  - `management.endpoints.web.exposure.include=health,info,metrics` (at minimum).
  - `management.endpoint.health.show-details=when_authorized` (or `always` for dev profile) so the `DslHealthIndicator` details are visible.
  - `management.info.env.enabled=true` and `management.info.java.enabled=true` where useful.
- [ ] `InfoContributor` (or `build-info.properties` via `bootBuildInfo`) populates `/actuator/info` with DSL/runtime version + build metadata.
- [ ] Existing `DslHealthIndicator` surfaces under `/actuator/health` with the correct health indicator name.
- [ ] Boot smoke test asserts `/actuator/health` returns 200 and includes the DSL indicator; `/actuator/info` returns version; `/actuator/metrics` lists JVM + DSL-related metrics.
- [ ] `./gradlew spotlessApply && ./gradlew build` passes; no new security holes (actuator endpoints kept behind existing Keycloak/auth where applicable — note in plan if `when_authorized` requires auth wiring).
- [ ] `docs/architecture-backend.md` updated with the actuator endpoint list + which are exposed by default.

## Out of scope

- Prometheus/Grafana scraping config (separate ops task).
- Custom metrics instrumentation beyond what already exists (DSL metrics emission is a follow-up).
- Securing actuator beyond the existing auth posture.

## Files to create / modify

- `backend/starter/build.gradle` — dep scope change.
- `backend/starter/src/main/resources/application.yml` — management config.
- `backend/starter/src/main/java/.../DslInfoContributor.java` — new (or rely on `bootBuildInfo`).
- `backend/starter/src/test/java/.../ActuatorEndpointsTest.java` — new boot smoke.
- `docs/architecture-backend.md` — document exposed endpoints.

## Notes for the execution loop

- Confirm the exact `DslHealthIndicator` class name + indicator name via codegraph before wiring exposure.
- If Keycloak security (T51) gates `/actuator/**`, expose `health` and `info` as permitAll, keep `metrics` authenticated — follow existing `SecurityFilterChain` posture rather than opening everything.
- Prefer `build-info.properties` (Gradle `bootBuildInfo` task) over hand-rolling version strings.

## Build / test commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test
./gradlew :starter:bootRun   # then GET /actuator/health, /actuator/info, /actuator/metrics
```
