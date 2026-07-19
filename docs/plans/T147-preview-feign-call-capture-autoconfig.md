# T147 — Preview Feign HTTP call-capture autoconfiguration

## Goal

Add a conditional Spring Boot autoconfiguration in `:starter` that records Feign HTTP requests as external call events. When a DSL helper/transaction uses a Feign client, the preview/explain reports must show the call under the `http` type with URL, method, and payload metadata.

## Tier

backend

## Files to create

- `backend/starter/src/main/java/cbs/nova/starter/config/FeignCallAutoConfiguration.java`
  `@AutoConfiguration` activated only when `feign.RequestInterceptor` is present on the classpath. Registers a single `RequestInterceptor` bean named `externalCallFeignInterceptor` that publishes HTTP call events.

- `backend/starter/src/main/java/cbs/nova/starter/capture/ExternalCallFeignInterceptor.java`
  Implements `feign.RequestInterceptor`. In `apply(RequestTemplate)` it records an `http` external call:
  - `type`: `http`
  - `target`: resolved request URL (`template.path()` + base URL if available; otherwise use `template.feignTarget().url()`)
  - `operation`: HTTP method (`GET`, `POST`, etc.)
  - `metadata`: map containing `url`, `method`, `headers` (redacted if desired), and a summary of the request body byte length or string content.

## Files to modify

- `backend/gradle/libs.versions.toml`
  Add an OpenFeign version (e.g. `[versions] openfeign = "13.6"`) and a library entry `openfeign-core = { module = "io.github.openfeign:feign-core", version.ref = "openfeign" }`.

- `backend/starter/build.gradle`
  Add `compileOnly libs.openfeign.core` (so the autoconfig is optional for consumers) and `testImplementation libs.openfeign.core`.

- `backend/starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  Append `cbs.nova.starter.config.FeignCallAutoConfiguration`.

## Files to create (tests)

- `backend/starter/src/test/java/cbs/nova/starter/capture/FeignCallAutoConfigurationTest.java`
  Test that:
  1. Builds a Feign client manually with the `externalCallFeignInterceptor` bean.
  2. Calls a stub endpoint (use JDK `HttpServer` or WireMock).
  3. Asserts `ExternalCallTracker.getGlobalCounts()` contains `http=1` and that the recorded call has the expected target/operation/method metadata.

## Acceptance criteria

- [ ] Autoconfiguration only activates when OpenFeign `RequestInterceptor` is on the classpath (`@ConditionalOnClass(name = "feign.RequestInterceptor")`).
- [ ] A single `RequestInterceptor` bean named `externalCallFeignInterceptor` is produced.
- [ ] Feign clients created with this interceptor record one `http` external call per request.
- [ ] The recorded `target` contains the absolute URL, the `operation` is the HTTP method, and metadata includes the method name.
- [ ] When Feign is absent from the classpath, the autoconfig silently does nothing and the Spring context still starts.
- [ ] New unit test passes and `./gradlew spotlessApply` is clean.

## Build / test commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test --tests '*FeignCallAutoConfigurationTest*'
```

## Implementation notes

- Do not pull in Spring Cloud OpenFeign unless the project already uses it. Plain `feign-core` is enough for the interceptor contract and keeps the starter lightweight.
- The interceptor must be usable both with manual `Feign.builder()` usage and with Spring Cloud Feign if a consumer adds the Spring Cloud starter later.
- Reuse `ExternalCallTracker.record(...)` for now, consistent with T146.
- Avoid logging sensitive headers by default; include only the method, URL, and a body summary (e.g. string length).

## Commit message

```
feat(T147): autoconfigure Feign HTTP call capture for preview/explain
```
