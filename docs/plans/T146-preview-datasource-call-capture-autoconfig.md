# T146 — Preview DataSource call-capture autoconfiguration

## Goal

Add a Spring Boot autoconfiguration in `:starter` that transparently proxies every `DataSource` bean and records JDBC operations as external call events. The captured events feed the existing `ExternalCallTracker` so that `/api/dsl/preview/{name}` and `/api/dsl/explain/{name}` reports include real database calls made by DSL helpers/transactions.

## Tier

backend

## Files to create

- `backend/starter/src/main/java/cbs/nova/starter/config/DataSourceCallAutoConfiguration.java`
  `@AutoConfiguration` activated by `DataSource` on the classpath and a `DataSource` bean in the context. Registers a `BeanPostProcessor` that wraps beans of type `DataSource`.

- `backend/starter/src/main/java/cbs/nova/starter/capture/DataSourceProxyBeanPostProcessor.java`
  Post-processor that returns a JDK dynamic proxy around each `DataSource`. The proxy intercepts `getConnection()` and returns a wrapped `Connection`.

- `backend/starter/src/main/java/cbs/nova/starter/capture/ConnectionInvocationHandler.java`
  Wraps `Connection`. Intercepts `prepareStatement(String)` / `createStatement()` / `prepareCall(String)` and returns a wrapped statement. Records the datasource URL as the call target.

- `backend/starter/src/main/java/cbs/nova/starter/capture/PreparedStatementInvocationHandler.java`
  Wraps `PreparedStatement` (and `Statement` / `CallableStatement`). Intercepts `executeQuery`, `executeUpdate`, `execute`, and `executeBatch`. On invocation, publishes a `database` external call to `ExternalCallTracker` with:
  - `type`: `database`
  - `target`: JDBC URL of the datasource
  - `operation`: SQL verb (`SELECT`, `INSERT`, `UPDATE`, `DELETE`, `BATCH`, …) or the first token of the SQL
  - `metadata`: map containing `sql` (and `batchSize` for batches if available)

## Files to modify

- `backend/starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  Append `cbs.nova.starter.config.DataSourceCallAutoConfiguration`.

## Files to create (tests)

- `backend/starter/src/test/java/cbs/nova/starter/capture/DataSourceCallAutoConfigurationTest.java`
  `@SpringBootTest`-style or plain Spring context test that:
  1. Creates an in-memory `DataSource` bean.
  2. Wires the `ExternalCallTracker`.
  3. Runs a `JdbcTemplate.query` / `JdbcTemplate.update` against the wrapped datasource.
  4. Asserts that `ExternalCallTracker.getGlobalCounts()` contains `database=1` and that the recorded call has the expected target/operation/sql metadata.

## Acceptance criteria

- [ ] Autoconfiguration is conditional: it only activates when `javax.sql.DataSource` is on the classpath and at least one `DataSource` bean exists (`@ConditionalOnClass` + `@ConditionalOnBean`).
- [ ] Multiple `DataSource` beans are each wrapped exactly once; calling a non-DataSource bean is a no-op.
- [ ] A JDBC query/update made through the wrapped datasource is recorded as a `database` external call.
- [ ] The recorded target is the datasource JDBC URL and the operation is derived from the SQL.
- [ ] No exceptions are thrown when the datasource does not expose a URL eagerly; the target may be resolved lazily from connection metadata.
- [ ] New unit test passes and `./gradlew spotlessApply` is clean.

## Build / test commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test --tests '*DataSourceCallAutoConfigurationTest*'
```

## Implementation notes

- Use `java.lang.reflect.Proxy` or Spring's `ProxyFactory` to wrap `DataSource`/`Connection`/`PreparedStatement`. JDK dynamic proxy is enough because all three are interfaces.
- The handler classes should delegate every method to the real object and only intercept the methods listed above.
- Keep the call metadata minimal but stable; avoid trying to extract bound parameters unless JDBC 4.2 `PreparedStatement.getParameterMetaData()` is reliably available.
- Reuse the existing `cbs.nova.starter.ExternalCallTracker.record(...)` method. Although the class is marked `@Deprecated(forRemoval = true)`, it is still the active capture surface used by `DevDslRuntime`; a later task can migrate all listeners to the replacement publisher.

## Commit message

```
feat(T146): autoconfigure DataSource JDBC call capture for preview/explain
```
