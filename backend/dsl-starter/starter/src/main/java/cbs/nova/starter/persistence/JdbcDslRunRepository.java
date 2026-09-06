package cbs.nova.starter.persistence;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.config.properties.DslRunPersistenceProperties;
import cbs.nova.starter.converter.DslRunMapper;
import cbs.nova.starter.entity.DslRunEntity;
import com.github.squigglesql.squigglesql.FunctionCall;
import com.github.squigglesql.squigglesql.Matchable;
import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  public JdbcDslRunRepository(NamedParameterJdbcTemplate jdbcTemplate,
          DslRunJdbcRepository delegate,
          DslRunMapper mapper, FieldEncryptor encryptor,
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

    delegate.findByRunId(entity.getRunId()).ifPresent(existing -> entity.setId(existing.getId()));
    return mapper.toDomain(decryptEntity(delegate.save(entity)));
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

    DslRunTable t = new DslRunTable(tableName);
    TableReference r = t.refer();

    ExtendedSelectQuery countQuery = new ExtendedSelectQuery();
    countQuery.addToSelection(Literal.unsafe("COUNT(*)"));
    addSearchCriteria(countQuery, t, r, processName, status, mode, correlationId);
    int total = Objects.requireNonNull(
            jdbcTemplate.queryForObject(countQuery.toString(), Map.of(), Integer.class));

    ExtendedSelectQuery dataQuery = new ExtendedSelectQuery();
    addFullSelection(dataQuery, t, r);
    addSearchCriteria(dataQuery, t, r, processName, status, mode, correlationId);
    dataQuery.addOrder(r.get(t.startedAt), false);
    dataQuery.limit(limit);
    dataQuery.offset(offset);
    List<DslRun> items = jdbcTemplate.query(dataQuery.toString(),
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
    DslRunEntity entity = delegate.findByRunId(runId)
            .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
    entity.setStatus(status);
    entity.setOutputJson(encryptor.encrypt(output));
    entity.setErrorMessage(encryptor.encrypt(error));
    entity.setContextJson(encryptor.encrypt(contextJson));
    entity.setFinishedAt(finishedAt);
    return mapper.toDomain(decryptEntity(delegate.save(entity)));
  }

  @Override
  public int updateFinishedIfRunning(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson) {
    return delegate.updateFinishedIfRunning(runId, status, encryptor.encrypt(output),
            encryptor.encrypt(error), encryptor.encrypt(contextJson), finishedAt);
  }

  @Override
  public int purgeFinishedBefore(
          @NonNull Instant cutoff,
          int batchSize,
          @NonNull Consumer<List<String>> onBatchBeforeParentDelete) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
    }
    DslRunTable t = new DslRunTable(tableName);

    int total = 0;
    while (true) {
      TableReference selectRef = t.refer();
      ExtendedSelectQuery select = new ExtendedSelectQuery();
      select.addToSelection(selectRef.get(t.id));
      select.addToSelection(selectRef.get(t.runId));
      select.addCriteria(Criteria.less(selectRef.get(t.finishedAt), Literal.of(cutoff)));
      select.addCriteria(Criteria.notEqual(selectRef.get(t.status),
              Literal.of(DslRunStatus.RUNNING.name())));
      select.limit(batchSize);
      List<PurgeBatchRow> batch = jdbcTemplate.query(select.toString(),
              (rs, rowNum) -> new PurgeBatchRow(rs.getLong(1), rs.getString(2)));
      if (batch.isEmpty()) {
        break;
      }
      onBatchBeforeParentDelete.accept(batch.stream().map(PurgeBatchRow::runId).toList());
      delegate.deleteAllById(batch.stream().map(PurgeBatchRow::id).toList());
      total += batch.size();
      if (batch.size() < batchSize) {
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
    DslRunTable t = new DslRunTable(tableName);

    ExtendedSelectQuery statusQuery = new ExtendedSelectQuery();
    TableReference statusRef = t.refer();
    statusQuery.addToSelection(statusRef.get(t.status));
    statusQuery.addToSelection(Literal.unsafe("COUNT(*)"));
    statusQuery.addGroupBy(statusRef.get(t.status));
    statusQuery.addOrder(statusRef.get(t.status), true);
    Map<String, Long> statusCounts = jdbcTemplate.query(statusQuery.toString(),
            (rs, rowNum) -> Map.entry(rs.getString(1), rs.getLong(2)))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                    LinkedHashMap::new));

    long totalRuns = statusCounts.values().stream().mapToLong(Long::longValue).sum();

    TableReference windowRef = t.refer();
    long windowRuns = countWhere(t,
            Criteria.notLess(windowRef.get(t.startedAt), Literal.of(windowStart)));
    TableReference failedRef = t.refer();
    long windowFailedRuns = countWhere(t, Criteria.and(
            Criteria.notLess(failedRef.get(t.startedAt), Literal.of(windowStart)),
            Criteria.equal(failedRef.get(t.status), Literal.of(DslRunStatus.FAILED.name()))));

    ExtendedSelectQuery topQuery = new ExtendedSelectQuery();
    TableReference topRef = t.refer();
    topQuery.addToSelection(topRef.get(t.processName));
    topQuery.addToSelection(Literal.unsafe("COUNT(*)"));
    topQuery.addGroupBy(topRef.get(t.processName));
    topQuery.addOrder(Literal.unsafe("COUNT(*)"), false);
    topQuery.addOrder(topRef.get(t.processName), true);
    topQuery.limit(topProcessesLimit);
    List<DslRunStats.ProcessRunCount> topProcesses = jdbcTemplate.query(topQuery.toString(),
            (rs, rowNum) -> new DslRunStats.ProcessRunCount(rs.getString(1), rs.getLong(2)));

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

    DslRunTable t = new DslRunTable(tableName);
    TableReference r = t.refer();

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(minuteBucket(t, r));
    query.addToSelection(r.get(t.status));
    query.addToSelection(Literal.unsafe("COUNT(*)"));
    query.addCriteria(Criteria.notLess(r.get(t.startedAt), Literal.of(windowStart)));
    query.addCriteria(Criteria.less(r.get(t.startedAt), Literal.of(windowEnd)));
    query.addGroupBy(minuteBucket(t, r));
    query.addGroupBy(r.get(t.status));
    query.addOrder(minuteBucket(t, r), true);
    query.addOrder(r.get(t.status), true);

    List<RunTimeseriesBucket> minuteRows = jdbcTemplate.query(query.toString(),
            (rs, rowNum) -> new RunTimeseriesBucket(
                    rs.getTimestamp(1).toInstant(),
                    rs.getString(2),
                    rs.getLong(3)));

    return foldMinuteBuckets(minuteRows, windowStart, bucketSeconds);
  }

  private long countWhere(DslRunTable t, Criteria criteria) {
    TableReference r = t.refer();
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(Literal.unsafe("COUNT(*)"));
    query.addCriteria(criteria);
    return Objects
            .requireNonNull(jdbcTemplate.queryForObject(query.toString(), Map.of(), Long.class));
  }

  private static FunctionCall minuteBucket(DslRunTable t, TableReference r) {
    return new FunctionCall("date_trunc", Literal.of("minute"), r.get(t.startedAt));
  }

  private static FunctionCall lower(Matchable argument) {
    return new FunctionCall("LOWER", argument);
  }

  private static void addSearchCriteria(ExtendedSelectQuery query, DslRunTable t, TableReference r,
          @Nullable String processName, @Nullable String status, @Nullable String mode,
          @Nullable String correlationId) {
    if (processName != null) {
      query.addCriteria(Criteria.equal(r.get(t.processName), Literal.of(processName)));
    }
    if (status != null) {
      query.addCriteria(Criteria.equal(lower(r.get(t.status)), lower(Literal.of(status))));
    }
    if (mode != null) {
      FunctionCall nullIfBlank = new FunctionCall("NULLIF", r.get(t.executionMode), Literal.of(""));
      FunctionCall coalesced = new FunctionCall("COALESCE", nullIfBlank, Literal.of("RUN"));
      query.addCriteria(Criteria.equal(lower(coalesced), lower(Literal.of(mode))));
    }
    if (correlationId != null && !correlationId.isBlank()) {
      query.addCriteria(Criteria.equal(r.get(t.correlationId), Literal.of(correlationId)));
    }
  }

  private static void addFullSelection(ExtendedSelectQuery query, DslRunTable t, TableReference r) {
    query.addToSelection(r.get(t.id));
    query.addToSelection(r.get(t.runId));
    query.addToSelection(r.get(t.processName));
    query.addToSelection(r.get(t.status));
    query.addToSelection(r.get(t.inputJson));
    query.addToSelection(r.get(t.outputJson));
    query.addToSelection(r.get(t.errorMessage));
    query.addToSelection(r.get(t.contextJson));
    query.addToSelection(r.get(t.startedAt));
    query.addToSelection(r.get(t.finishedAt));
    query.addToSelection(r.get(t.executionMode));
    query.addToSelection(r.get(t.triggeredBy));
    query.addToSelection(r.get(t.correlationId));
  }

  private static List<RunTimeseriesBucket> foldMinuteBuckets(
          List<RunTimeseriesBucket> minuteRows,
          Instant windowStart,
          long bucketSeconds) {
    Map<Long, Map<String, Long>> byIndex = new LinkedHashMap<>();
    for (RunTimeseriesBucket row : minuteRows) {
      long secondsFromStart = Duration.between(windowStart, row.bucketStart()).getSeconds();
      long bucketIndex = secondsFromStart / bucketSeconds;
      byIndex.computeIfAbsent(bucketIndex, k -> new LinkedHashMap<>())
              .merge(row.status(), row.count(), Long::sum);
    }
    List<RunTimeseriesBucket> out = new ArrayList<>();
    for (var entry : byIndex.entrySet()) {
      Instant bucketStart = windowStart.plusSeconds(entry.getKey() * bucketSeconds);
      for (var statusEntry : entry.getValue().entrySet()) {
        out.add(new RunTimeseriesBucket(bucketStart, statusEntry.getKey(), statusEntry.getValue()));
      }
    }
    out.sort(Comparator.comparing(RunTimeseriesBucket::bucketStart)
            .thenComparing(RunTimeseriesBucket::status));
    return out;
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

  private record PurgeBatchRow(Long id, String runId) {
  }

  private static final class DslRunTable {

    final Table table;
    final TableColumn id;
    final TableColumn runId;
    final TableColumn processName;
    final TableColumn status;
    final TableColumn inputJson;
    final TableColumn outputJson;
    final TableColumn errorMessage;
    final TableColumn contextJson;
    final TableColumn startedAt;
    final TableColumn finishedAt;
    final TableColumn executionMode;
    final TableColumn triggeredBy;
    final TableColumn correlationId;

    DslRunTable(String name) {
      table = new Table(name);
      id = table.get("id");
      runId = table.get("run_id");
      processName = table.get("process_name");
      status = table.get("status");
      inputJson = table.get("input_json");
      outputJson = table.get("output_json");
      errorMessage = table.get("error_message");
      contextJson = table.get("context_json");
      startedAt = table.get("started_at");
      finishedAt = table.get("finished_at");
      executionMode = table.get("execution_mode");
      triggeredBy = table.get("triggered_by");
      correlationId = table.get("correlation_id");
    }

    TableReference refer() {
      return table.refer();
    }
  }
}
