package cbs.nova.starter.persistence;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.converter.DslRunMapper;
import cbs.nova.starter.entity.DslRunEntity;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class JdbcDslRunRepository implements DslRunRepository, DslRunStatsRepository {

  private final ExtendedSelectQueryExecutor dslQueries;
  private final DslRunJdbcRepository delegate;
  private final DslRunMapper mapper;
  private final DslRunEncryption encryption;
  private final String tableName;
  private final DslRunEntityRowMapper entityRowMapper = new DslRunEntityRowMapper();

  @Override
  public @NonNull DslRun save(@NonNull DslRun run) {
    DslRunEntity entity = mapper.toEntity(run);
    encryption.encrypt(entity);

    delegate.findByRunId(entity.getRunId()).ifPresent(existing -> entity.setId(existing.getId()));
    return mapper.toDomain(encryption.decrypt(delegate.save(entity)));
  }

  @Override
  public @NonNull Optional<DslRun> findByRunId(@NonNull String runId) {
    return delegate.findByRunId(runId).map(e -> mapper.toDomain(encryption.decrypt(e)));
  }

  @Override
  public @NonNull List<DslRun> findByProcessName(@NonNull String processName) {
    return delegate.findByProcessName(processName).stream()
            .map(e -> mapper.toDomain(encryption.decrypt(e)))
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

    DslRunTableColumns t = new DslRunTableColumns(tableName);
    TableReference r = t.refer();

    ExtendedSelectQuery countQuery = searchQuery(t, r, processName, status, mode, correlationId)
            .select(Literal.unsafe("COUNT(*)"))
            .build();
    int total = dslQueries.queryForObject(countQuery, Integer.class);

    ExtendedSelectQuery dataQuery = searchQuery(t, r, processName, status, mode, correlationId)
            .select(DslRunQueryCriteria.fullSelection(t, r).toArray(Selectable[]::new))
            .orderByDesc(r.get(t.startedAt))
            .limit(limit)
            .offset(offset)
            .build();
    List<DslRun> items = dslQueries.query(dataQuery, entityRowMapper).stream()
            .map(e -> mapper.toDomain(encryption.decrypt(e)))
            .collect(Collectors.toList());
    return new DslRunSearchResult(items, total);
  }

  private ExtendedSelectQueryExecutor.Builder searchQuery(
          DslRunTableColumns t,
          TableReference r,
          @Nullable String processName,
          @Nullable String status,
          @Nullable String mode,
          @Nullable String correlationId) {
    return dslQueries.select()
            .whereIf(processName != null,
                    () -> DslRunQueryCriteria.matchesProcessName(t, r, processName))
            .whereIf(status != null,
                    () -> DslRunQueryCriteria.matchesStatusIgnoreCase(t, r, status))
            .whereIf(mode != null,
                    () -> DslRunQueryCriteria.matchesModeIgnoreCase(t, r, mode))
            .whereIf(correlationId != null && !correlationId.isBlank(),
                    () -> DslRunQueryCriteria.matchesCorrelationId(t, r, correlationId));
  }

  @Override
  public @NonNull DslRun updateFinished(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson) {
    delegate.updateFinishedByRunId(runId, status, encryption.encrypt(output),
            encryption.encrypt(error), encryption.encrypt(contextJson), finishedAt);
    return delegate.findByRunId(runId)
            .map(e -> mapper.toDomain(encryption.decrypt(e)))
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
    return delegate.updateFinishedIfRunning(runId, status, encryption.encrypt(output),
            encryption.encrypt(error), encryption.encrypt(contextJson), finishedAt);
  }

  @Override
  public int purgeFinishedBefore(
          @NonNull Instant cutoff,
          int batchSize,
          @NonNull Consumer<List<String>> onBatchBeforeParentDelete) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
    }
    DslRunTableColumns t = new DslRunTableColumns(tableName);

    int total = 0;
    // TODO: refactor 'while' loop, to a 'for' loop
    while (true) {
      TableReference selectRef = t.refer();
      ExtendedSelectQuery select = dslQueries.select()
              .select(selectRef.get(t.id), selectRef.get(t.runId))
              .where(Criteria.less(selectRef.get(t.finishedAt), Literal.of(cutoff)))
              .where(Criteria.notEqual(selectRef.get(t.status),
                      Literal.of(DslRunStatus.RUNNING.name())))
              .limit(batchSize)
              .build();
      List<PurgeBatchRow> batch = dslQueries.query(select,
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
    DslRunTableColumns t = new DslRunTableColumns(tableName);

    TableReference statusRef = t.refer();
    ExtendedSelectQuery statusQuery = dslQueries.select()
            .select(statusRef.get(t.status), Literal.unsafe("COUNT(*)"))
            .groupBy(statusRef.get(t.status))
            .orderByAsc(statusRef.get(t.status))
            .build();
    Map<String, Long> statusCounts = dslQueries.query(statusQuery,
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

    TableReference topRef = t.refer();
    ExtendedSelectQuery topQuery = dslQueries.select()
            .select(topRef.get(t.processName), Literal.unsafe("COUNT(*)"))
            .groupBy(topRef.get(t.processName))
            .orderByDesc(Literal.unsafe("COUNT(*)"))
            .orderByAsc(topRef.get(t.processName))
            .limit(topProcessesLimit)
            .build();
    List<DslRunStats.ProcessRunCount> topProcesses = dslQueries.query(topQuery,
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

    DslRunTableColumns t = new DslRunTableColumns(tableName);
    TableReference r = t.refer();

    ExtendedSelectQuery query = dslQueries.select()
            .select(DslRunQueryCriteria.minuteBucket(t, r), r.get(t.status),
                    Literal.unsafe("COUNT(*)"))
            .where(Criteria.notLess(r.get(t.startedAt), Literal.of(windowStart)))
            .where(Criteria.less(r.get(t.startedAt), Literal.of(windowEnd)))
            .groupBy(DslRunQueryCriteria.minuteBucket(t, r), r.get(t.status))
            .orderByAsc(DslRunQueryCriteria.minuteBucket(t, r))
            .orderByAsc(r.get(t.status))
            .build();

    List<RunTimeseriesBucket> minuteRows = dslQueries.query(query,
            (rs, rowNum) -> new RunTimeseriesBucket(
                    rs.getTimestamp(1).toInstant(),
                    rs.getString(2),
                    rs.getLong(3)));

    return RunTimeseriesBucketing.foldMinuteBuckets(minuteRows, windowStart, bucketSeconds);
  }

  private long countWhere(DslRunTableColumns t, Criteria criteria) {
    TableReference r = t.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(Literal.unsafe("COUNT(*)"))
            .where(criteria)
            .build();
    return dslQueries.queryForObject(query, Long.class);
  }

  private record PurgeBatchRow(Long id, String runId) {
  }
}
