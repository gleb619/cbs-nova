package cbs.nova.starter.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RunTimeseriesBucketing {

  public static List<RunTimeseriesBucket> foldMinuteBuckets(
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
}
