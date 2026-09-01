package cbs.nova.starter.persistence;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.dsl.history.DslRunStatus;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JdbcDslRunRepository implements DslRunRepository, DslRunStatsRepository {

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

    if (delegate.findByRunId(entity.getRunId()).isPresent()) {
      jdbcTemplate.update(getUpsertStatement(), insertParams(entity));
      return findByRunId(entity.getRunId())
              .orElseThrow(() -> new IllegalStateException("Run not found: " + entity.getRunId()));
    }

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(getInsertStatement(), insertParams(entity), keyHolder, new String[]{"id"});
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
  public @NonNull DslRunSearchResult search(
          @Nullable String processName,
          @Nullable String status,
          @Nullable String mode,
          @Nullable String correlationId,
          int offset,
          int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }

    List<String> predicates = new ArrayList<>();
    MapSqlParameterSource params = new MapSqlParameterSource();
    if (processName != null) {
      predicates.add("process_name = :processName");
      params.addValue("processName", processName);
    }
    if (status != null) {
      predicates.add("LOWER(status) = LOWER(:status)");
      params.addValue("status", status);
    }
    if (mode != null) {
      predicates.add("LOWER(COALESCE(NULLIF(execution_mode, ''), 'RUN')) = LOWER(:mode)");
      params.addValue("mode", mode);
    }
    predicates.add("(:correlationId IS NULL OR correlation_id = :correlationId)");
    params.addValue("correlationId", correlationId);
    String where = predicates.isEmpty() ? "" : "WHERE " + String.join(" AND ", predicates);

    int total = jdbcTemplate.queryForObject(getSearchCountStatement(where), params, Integer.class);
    params.addValue("limit", limit).addValue("offset", offset);
    List<DslRun> items = jdbcTemplate.query(
            getSearchStatement(where),
            params,
            (rs, rowNum) -> mapper.toDomain(decryptEntity(mapEntity(rs))));
    return new DslRunSearchResult(items, total);
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

    MapSqlParameterSource params = finishParams(runId, status, output, error, contextJson,
            finishedAt);

    int updated = jdbcTemplate.update(sql, params);
    if (updated == 0) {
      throw new IllegalStateException("Run not found: " + runId);
    }
    return findByRunId(runId)
            .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
  }

  @Override
  public int updateFinishedIfRunning(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson) {
    String sql = getGuardedUpdateStatement();

    MapSqlParameterSource params = finishParams(runId, status, output, error, contextJson,
            finishedAt)
            .addValue("expectedStatus", DslRunStatus.RUNNING.name());

    return jdbcTemplate.update(sql, params);
  }

  @Override
  public int purgeFinishedBefore(
          @NonNull Instant cutoff,
          int batchSize,
          @NonNull Consumer<List<String>> onBatchBeforeParentDelete) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
    }
    String selectSql = "SELECT run_id FROM %s WHERE finished_at < :cutoff AND status <> :runningStatus LIMIT :batchSize"
            .formatted(tableName);
    String deleteSql = "DELETE FROM %s WHERE run_id IN (:ids)".formatted(tableName);
    MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("cutoff", Timestamp.from(cutoff))
            .addValue("runningStatus", DslRunStatus.RUNNING.name())
            .addValue("batchSize", batchSize);

    int total = 0;
    while (true) {
      List<String> ids = jdbcTemplate.queryForList(selectSql, params, String.class);
      if (ids.isEmpty()) {
        break;
      }
      onBatchBeforeParentDelete.accept(ids);
      int deleted = jdbcTemplate.update(deleteSql, new MapSqlParameterSource("ids", ids));
      total += deleted;
      if (ids.size() < batchSize) {
        break;
      }
    }
    return total;
  }

  @Override
  public @NonNull DslRunStats stats(@NonNull Instant windowStart, int topProcessesLimit) {
    if (topProcessesLimit <= 0) {
      throw new IllegalArgumentException(
              "topProcessesLimit must be positive, was " + topProcessesLimit);
    }

    MapSqlParameterSource windowParams = new MapSqlParameterSource()
            .addValue("windowStart", Timestamp.from(windowStart))
            .addValue("failedStatus", DslRunStatus.FAILED.name());

    Map<String, Long> statusCounts = new LinkedHashMap<>();
    jdbcTemplate.query(getStatusCountsStatement(), windowParams, rs -> {
      statusCounts.put(rs.getString(1), rs.getLong(2));
    });

    long totalRuns = statusCounts.values().stream().mapToLong(Long::longValue).sum();

    long[] window = new long[2];
    jdbcTemplate.query(getWindowStatement(), windowParams, rs -> {
      window[0] = rs.getLong(1);
      window[1] = rs.getLong(2);
    });
    long windowRuns = window[0];
    long windowFailedRuns = window[1];

    MapSqlParameterSource topParams = new MapSqlParameterSource()
            .addValue("topProcessesLimit", topProcessesLimit);
    List<DslRunStats.ProcessRunCount> topProcesses = jdbcTemplate
            .query(getTopProcessesStatement(), topParams,
                    (rs, i) -> new DslRunStats.ProcessRunCount(rs.getString(1), rs.getLong(2)));

    double failureRate = windowRuns == 0 ? 0.0 : (double) windowFailedRuns / windowRuns;
    return new DslRunStats(totalRuns, statusCounts, windowRuns, windowFailedRuns, failureRate,
            topProcesses);
  }

  @Override
  public @NonNull List<RunTimeseriesBucket> timeseries(@NonNull Instant windowStart,
          @NonNull Instant windowEnd, @NonNull Duration bucketSize) {
    if (!windowEnd.isAfter(windowStart)) {
      throw new IllegalArgumentException(
              "windowEnd must be after windowStart, was windowStart=" + windowStart
                      + " windowEnd=" + windowEnd);
    }
    if (bucketSize.isZero() || bucketSize.isNegative()) {
      throw new IllegalArgumentException(
              "bucketSize must be positive, was " + bucketSize);
    }
    long bucketSeconds = bucketSize.getSeconds();
    long windowSeconds = Duration.between(windowStart, windowEnd).getSeconds();
    if (windowSeconds % bucketSeconds != 0) {
      throw new IllegalArgumentException(
              "windowSeconds (" + windowSeconds + ") must be divisible by bucketSeconds ("
                      + bucketSeconds + ") so bucket boundaries are stable");
    }

    MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("windowStart", Timestamp.from(windowStart))
            .addValue("windowEnd", Timestamp.from(windowEnd));

    List<RunTimeseriesBucket> minuteRows = jdbcTemplate.query(
            getTimeseriesStatement(), params,
            (rs, rowNum) -> new RunTimeseriesBucket(
                    rs.getTimestamp(1).toInstant(),
                    rs.getString(2),
                    rs.getLong(3)));

    return foldMinuteBuckets(minuteRows, windowStart, bucketSeconds);
  }

  private static List<RunTimeseriesBucket> foldMinuteBuckets(
          List<RunTimeseriesBucket> minuteRows,
          Instant windowStart,
          long bucketSeconds) {
    java.util.Map<Long, java.util.Map<String, Long>> byIndex = new java.util.LinkedHashMap<>();
    for (RunTimeseriesBucket row : minuteRows) {
      long secondsFromStart = Duration.between(windowStart, row.bucketStart()).getSeconds();
      long bucketIndex = secondsFromStart / bucketSeconds;
      byIndex.computeIfAbsent(bucketIndex, k -> new java.util.LinkedHashMap<>())
              .merge(row.status(), row.count(), Long::sum);
    }
    List<RunTimeseriesBucket> out = new java.util.ArrayList<>();
    for (var entry : byIndex.entrySet()) {
      Instant bucketStart = windowStart.plusSeconds(entry.getKey() * bucketSeconds);
      for (var statusEntry : entry.getValue().entrySet()) {
        out.add(new RunTimeseriesBucket(bucketStart, statusEntry.getKey(), statusEntry.getValue()));
      }
    }
    out.sort(java.util.Comparator.comparing(RunTimeseriesBucket::bucketStart)
            .thenComparing(RunTimeseriesBucket::status));
    return out;
  }

  private MapSqlParameterSource insertParams(DslRunEntity entity) {
    return new MapSqlParameterSource()
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
            .addValue("executionMode", entity.getExecutionMode())
            .addValue("triggeredBy", entity.getTriggeredBy())
            .addValue("correlationId", entity.getCorrelationId());
  }

  private MapSqlParameterSource finishParams(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @Nullable String contextJson,
          @NonNull Instant finishedAt) {
    return new MapSqlParameterSource()
            .addValue("status", status)
            .addValue("outputJson", encryptor.encrypt(output))
            .addValue("errorMessage", encryptor.encrypt(error))
            .addValue("contextJson", encryptor.encrypt(contextJson))
            .addValue("finishedAt", Timestamp.from(finishedAt))
            .addValue("runId", runId);
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

  private String getSearchCountStatement(String where) {
    return "SELECT COUNT(*) FROM %s %s".formatted(tableName, where);
  }

  private String getSearchStatement(String where) {
    return "SELECT * FROM %s %s ORDER BY started_at DESC LIMIT :limit OFFSET :offset"
            .formatted(tableName, where);
  }

  private DslRunEntity mapEntity(ResultSet rs) throws SQLException {
    DslRunEntity entity = new DslRunEntity();
    entity.setId(rs.getLong("id"));
    entity.setRunId(rs.getString("run_id"));
    entity.setProcessName(rs.getString("process_name"));
    entity.setStatus(rs.getString("status"));
    entity.setInputJson(rs.getString("input_json"));
    entity.setOutputJson(rs.getString("output_json"));
    entity.setErrorMessage(rs.getString("error_message"));
    entity.setContextJson(rs.getString("context_json"));
    Timestamp startedAt = rs.getTimestamp("started_at");
    entity.setStartedAt(startedAt != null ? startedAt.toInstant() : null);
    Timestamp finishedAt = rs.getTimestamp("finished_at");
    entity.setFinishedAt(finishedAt != null ? finishedAt.toInstant() : null);
    entity.setExecutionMode(rs.getString("execution_mode"));
    entity.setTriggeredBy(rs.getString("triggered_by"));
    entity.setCorrelationId(rs.getString("correlation_id"));
    return entity;
  }

  private String getInsertStatement() {
    return """
            INSERT INTO %s (run_id, process_name, status, input_json, output_json, error_message, context_json, started_at, finished_at, execution_mode, triggered_by, correlation_id)
            VALUES
            (:runId, :processName, :status, :inputJson, :outputJson, :errorMessage, :contextJson, :startedAt, :finishedAt, :executionMode, :triggeredBy, :correlationId)"""
            .formatted(tableName);
  }

  private String getUpsertStatement() {
    return """
            UPDATE %s SET
                process_name = :processName
              , status = :status
              , input_json = :inputJson
              , output_json = :outputJson
              , error_message = :errorMessage
              , context_json = :contextJson
              , started_at = :startedAt
              , finished_at = :finishedAt
              , execution_mode = :executionMode
              , triggered_by = :triggeredBy
              , correlation_id = :correlationId
            WHERE run_id = :runId""".formatted(tableName);
  }

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

  private String getGuardedUpdateStatement() {
    return """
            UPDATE %s SET
                status = :status
              , output_json = :outputJson
              , error_message = :errorMessage
              , context_json = :contextJson
              , finished_at = :finishedAt
            WHERE run_id = :runId
              AND status = :expectedStatus""".formatted(
            tableName);
  }

  private String getStatusCountsStatement() {
    return "SELECT status, COUNT(*) FROM %s GROUP BY status ORDER BY status".formatted(tableName);
  }

  private String getWindowStatement() {
    return """
            SELECT COUNT(*), COUNT(*) FILTER (WHERE status = :failedStatus)
            FROM %s WHERE started_at >= :windowStart""".formatted(tableName);
  }

  private String getTimeseriesStatement() {
    return """
            SELECT date_trunc('minute', started_at) AS bucket_minute
                 , status
                 , COUNT(*) AS run_count
            FROM %s
            WHERE started_at >= :windowStart
              AND started_at <  :windowEnd
            GROUP BY bucket_minute, status
            ORDER BY bucket_minute ASC, status ASC""".formatted(tableName);
  }

  private String getTopProcessesStatement() {
    return """
            SELECT process_name, COUNT(*)
            FROM %s
            GROUP BY process_name
            ORDER BY COUNT(*) DESC, process_name ASC
            LIMIT :topProcessesLimit""".formatted(tableName);
  }

}
