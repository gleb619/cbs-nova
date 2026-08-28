package cbs.nova.starter.persistence;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.config.properties.DslRunPersistenceProperties;
import cbs.nova.starter.converter.DslRunMapper;
import cbs.nova.starter.entity.DslRunEntity;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JDBC-backed {@link DslRunRepository} with configurable table name/schema, application-level field
 * encryption, MapStruct entity mapping, and a targeted update method for finishing a run.
 */
@RequiredArgsConstructor
public class JdbcDslRunRepository implements DslRunRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final DslRunJdbcRepository delegate;
  private final DslRunMapper mapper;
  private final FieldEncryptor encryptor;
  private final String tableName;

  public JdbcDslRunRepository(DataSource dataSource, DslRunJdbcRepository delegate,
          DslRunMapper mapper, FieldEncryptor encryptor,
          DslRunPersistenceProperties properties) {
    this(new NamedParameterJdbcTemplate(dataSource), delegate, mapper, encryptor, properties);
  }

  public JdbcDslRunRepository(NamedParameterJdbcTemplate jdbcTemplate,
          DslRunJdbcRepository delegate, DslRunMapper mapper, FieldEncryptor encryptor,
          DslRunPersistenceProperties properties) {
    this(jdbcTemplate, delegate, mapper, encryptor, qualifiedTableName(properties));
  }

  private static String qualifiedTableName(DslRunPersistenceProperties properties) {
    String table = properties.tableName() != null && !properties.tableName().isBlank()
            ? properties.tableName()
            : "dsl_runs";
    String schema = properties.schema();
    return (schema != null && !schema.isBlank()) ? schema + "." + table : table;
  }

  @Override
  public @NonNull DslRun save(@NonNull DslRun run) {
    DslRunEntity entity = mapper.toEntity(run);
    encryptEntity(entity);

    KeyHolder keyHolder = new GeneratedKeyHolder();
    String sql = getInsertStatement();

    MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("runId", entity.getRunId())
            .addValue("processName", entity.getProcessName())
            .addValue("status", entity.getStatus())
            .addValue("inputJson", entity.getInputJson())
            .addValue("outputJson", entity.getOutputJson())
            .addValue("errorMessage", entity.getErrorMessage())
            .addValue("contextJson", entity.getContextJson())
            .addValue("startedAt", Timestamp.from(entity.getStartedAt()))
            .addValue("finishedAt",
                    entity.getFinishedAt() != null ? Timestamp.from(entity.getFinishedAt()) : null)
            .addValue("executionMode", entity.getExecutionMode());

    jdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});
    if (keyHolder.getKey() != null) {
      entity.setId(keyHolder.getKey().longValue());
    }
    return mapper.toDomain(decryptEntity(entity));
  }

  @Override
  public @NonNull Optional<DslRun> findByRunId(@NonNull String runId) {
    return delegate.findByRunId(runId).map(e -> mapper.toDomain(decryptEntity(e)));
  }

  @Override
  public @NonNull List<DslRun> findByProcessName(@NonNull String processName) {
    return delegate.findByProcessName(processName).stream()
            .map(e -> mapper.toDomain(decryptEntity(e)))
            .collect(Collectors.toList());
  }

  @Override
  public @NonNull DslRun updateFinished(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson) {
    String sql = getUpdateStatement();

    MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("status", status)
            .addValue("outputJson", encryptor.encrypt(output))
            .addValue("errorMessage", encryptor.encrypt(error))
            .addValue("contextJson", encryptor.encrypt(contextJson))
            .addValue("finishedAt", Timestamp.from(finishedAt))
            .addValue("runId", runId);

    int updated = jdbcTemplate.update(sql, params);
    if (updated == 0) {
      throw new IllegalStateException("Run not found: " + runId);
    }
    return findByRunId(runId)
            .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
  }

  private void encryptEntity(DslRunEntity entity) {
    entity.setInputJson(encryptor.encrypt(entity.getInputJson()));
    entity.setOutputJson(encryptor.encrypt(entity.getOutputJson()));
    entity.setContextJson(encryptor.encrypt(entity.getContextJson()));
  }

  private DslRunEntity decryptEntity(DslRunEntity entity) {
    entity.setInputJson(encryptor.decrypt(entity.getInputJson()));
    entity.setOutputJson(encryptor.decrypt(entity.getOutputJson()));
    entity.setContextJson(encryptor.decrypt(entity.getContextJson()));
    return entity;
  }

  // TODO: it's better to remove native insert. ANd just work with a entity, so since we can work
  // only with
  // repository, we can handle enc work here
  // we can reuse
  // `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/persistence/DslRunNamingStrategy.java`
  // to customize table name
  private String getInsertStatement() {
    return """
            INSERT INTO %s (run_id, process_name, status, input_json, output_json, error_message, context_json, started_at, finished_at, execution_mode)
            VALUES
            (:runId, :processName, :status, :inputJson, :outputJson, :errorMessage, :contextJson, :startedAt, :finishedAt, :executionMode)"""
            .formatted(
                    tableName);
  }

  // TODO: it's better to remove native update. ANd just work with a entity, so since we can work
  // only with
  // repository, we can handle enc work here
  private String getUpdateStatement() {
    return """
            UPDATE %s SET
                status = :status
              , output_json = :outputJson
              , error_message = :errorMessage
              , context_json = :contextJson
              , finished_at = :finishedAt
            WHERE run_id = :runId""".formatted(
            tableName);
  }

}
