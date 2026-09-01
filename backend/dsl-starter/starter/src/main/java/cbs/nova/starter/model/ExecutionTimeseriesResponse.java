package cbs.nova.starter.model;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.persistence.RunTimeseriesBucket;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wire shape of {@code GET /api/executions/stats/timeseries}.
 *
 * <p>
 * The endpoint returns a uniform grid of (bucket, status) rows. Buckets run from
 * {@code windowStart} to {@code windowEnd} at {@code bucketMinutes} spacing, with every (bucket,
 * status) pair represented — zero-fill is the handler's responsibility so the dashboard can plot a
 * stable x-axis without sparse rows.
 *
 * <p>
 * Status keys use the same display casing as {@link ExecutionDto} (e.g. {@code Running},
 * {@code Completed}) so the frontend can reuse the existing status badge palette without a second
 * mapping layer.
 */
public record ExecutionTimeseriesResponse(
        Instant windowStart,
        Instant windowEnd,
        long bucketMinutes,
        List<BucketRow> buckets) {

  /**
   * One bucket's counts. {@code statusCounts} keys are display-cased status names; statuses with
   * zero runs in this bucket are absent from the map.
   */
  public record BucketRow(Instant bucketStart, Map<String, Long> statusCounts) {
  }

  /**
   * Zero-fill a list of narrow {@link RunTimeseriesBucket} rows into the wide {@link BucketRow}
   * shape the dashboard renders. The handler calls this after the store returns one row per
   * (bucket, status) pair that has at least one run; empty buckets are filled with the same status
   * set the populated buckets use so the axis stays consistent.
   *
   * <p>
   * The store emits minute-granularity buckets; this method folds adjacent minutes into the
   * requested {@code bucketSize} so the response always shows
   * {@code (windowEnd - windowStart) / bucketSize} rows aligned on {@code windowStart}. Rows that
   * fall outside the window are ignored.
   *
   * @param narrowRows
   *          rows returned by the store
   * @param windowStart
   *          inclusive window start
   * @param windowEnd
   *          exclusive window end
   * @param bucketSize
   *          bucket width; must be positive and divide evenly into {@code windowEnd - windowStart}
   */
  public static ExecutionTimeseriesResponse from(List<RunTimeseriesBucket> narrowRows,
          Instant windowStart, Instant windowEnd, Duration bucketSize) {
    long bucketSeconds = bucketSize.getSeconds();
    List<String> distinctStatuses = new ArrayList<>();
    Map<Long, Map<String, Long>> aggregated = new LinkedHashMap<>();
    for (RunTimeseriesBucket row : narrowRows) {
      long bucketIndex = Duration.between(windowStart, row.bucketStart()).getSeconds()
              / bucketSeconds;
      Map<String, Long> statusCounts = aggregated.computeIfAbsent(bucketIndex,
              k -> new LinkedHashMap<>());
      String displayStatus = displayStatus(row.status());
      statusCounts.merge(displayStatus, row.count(), Long::sum);
      if (!distinctStatuses.contains(displayStatus)) {
        distinctStatuses.add(displayStatus);
      }
    }

    long bucketCount = bucketSeconds == 0
            ? 0
            : Duration.between(windowStart, windowEnd).getSeconds() / bucketSeconds;
    List<BucketRow> rows = new ArrayList<>();
    for (long i = 0; i < bucketCount; i++) {
      Instant bucketStart = windowStart.plusSeconds(i * bucketSeconds);
      Map<String, Long> existing = aggregated.getOrDefault(i, Map.of());
      Map<String, Long> filled = new LinkedHashMap<>();
      for (String displayStatus : distinctStatuses) {
        filled.put(displayStatus, existing.getOrDefault(displayStatus, 0L));
      }
      rows.add(new BucketRow(bucketStart, filled));
    }

    return new ExecutionTimeseriesResponse(windowStart, windowEnd, bucketSize.toMinutes(),
            rows);
  }

  private static String displayStatus(String status) {
    if (status == null || status.isBlank()) {
      return "Running";
    }
    for (DslRunStatus candidate : DslRunStatus.values()) {
      if (candidate.name().equals(status)) {
        return candidate.name().charAt(0) + candidate.name().substring(1).toLowerCase(Locale.ROOT);
      }
    }
    return status.charAt(0) + status.substring(1).toLowerCase(Locale.ROOT);
  }
}
