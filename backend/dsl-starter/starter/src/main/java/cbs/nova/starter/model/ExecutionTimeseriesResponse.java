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


public record ExecutionTimeseriesResponse(
        Instant windowStart,
        Instant windowEnd,
        long bucketMinutes,
        List<BucketRow> buckets) {


  public record BucketRow(Instant bucketStart, Map<String, Long> statusCounts) {
  }


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
