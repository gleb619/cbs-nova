package cbs.nova.starter.model;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.persistence.RunTimeseriesBucket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pin the wire-shape zero-fill behavior of {@link ExecutionTimeseriesResponse#from}: empty buckets
 * must be emitted with zeros for every status seen anywhere in the window so the dashboard's x-axis
 * stays uniform and every bar has a consistent color set.
 */
class ExecutionTimeseriesResponseTest {

  @Test
  void fromZeroRowsEmitsUniformZeroGridUsingAllKnownStatuses() {
    Instant windowStart = Instant.parse("2026-08-13T10:00:00Z");
    Instant windowEnd = windowStart.plus(Duration.ofHours(2));

    ExecutionTimeseriesResponse response = ExecutionTimeseriesResponse.from(
            List.of(
                    new RunTimeseriesBucket(windowStart, "RUNNING", 1L),
                    new RunTimeseriesBucket(windowStart.plus(Duration.ofHours(1)), "FAILED", 2L)),
            windowStart, windowEnd, Duration.ofHours(1));

    assertThat(response.windowStart()).isEqualTo(windowStart);
    assertThat(response.windowEnd()).isEqualTo(windowEnd);
    assertThat(response.bucketMinutes()).isEqualTo(60L);
    assertThat(response.buckets()).hasSize(2);
    assertThat(response.buckets().get(0).bucketStart()).isEqualTo(windowStart);
    assertThat(response.buckets().get(0).statusCounts())
            .containsEntry("Running", 1L)
            .containsEntry("Failed", 0L);
    assertThat(response.buckets().get(1).bucketStart())
            .isEqualTo(windowStart.plus(Duration.ofHours(1)));
    assertThat(response.buckets().get(1).statusCounts())
            .containsEntry("Running", 0L)
            .containsEntry("Failed", 2L);
  }

  @Test
  void fromEmptyRowsEmitsEmptyBucketGrid() {
    Instant windowStart = Instant.parse("2026-08-13T10:00:00Z");
    Instant windowEnd = windowStart.plus(Duration.ofHours(3));

    ExecutionTimeseriesResponse response = ExecutionTimeseriesResponse.from(
            List.of(), windowStart, windowEnd, Duration.ofHours(1));

    assertThat(response.buckets()).hasSize(3);
    assertThat(response.buckets()).allSatisfy(b -> assertThat(b.statusCounts()).isEmpty());
  }

  @Test
  void fromAggregatesAdjacentMinutesIntoTheRequestedBucketWidth() {
    Instant windowStart = Instant.parse("2026-08-13T10:00:00Z");
    Instant windowEnd = windowStart.plus(Duration.ofMinutes(30));

    // 5-minute granularity from the SQL layer — the handler folds three
    // adjacent minutes into the 15-minute bucket. Each minute has one
    // COMPLETED run; the 15-minute bucket must report 3.
    ExecutionTimeseriesResponse response = ExecutionTimeseriesResponse.from(
            List.of(
                    new RunTimeseriesBucket(windowStart, "COMPLETED", 1L),
                    new RunTimeseriesBucket(windowStart.plus(Duration.ofMinutes(5)), "COMPLETED",
                            1L),
                    new RunTimeseriesBucket(windowStart.plus(Duration.ofMinutes(10)), "COMPLETED",
                            1L)),
            windowStart, windowEnd, Duration.ofMinutes(15));

    assertThat(response.buckets()).hasSize(2);
    assertThat(response.buckets().get(0).bucketStart()).isEqualTo(windowStart);
    assertThat(response.buckets().get(0).statusCounts()).containsEntry("Completed", 3L);
    assertThat(response.buckets().get(1).bucketStart())
            .isEqualTo(windowStart.plus(Duration.ofMinutes(15)));
    assertThat(response.buckets().get(1).statusCounts()).containsEntry("Completed", 0L);
  }

  @Test
  void fromMapsStatusCasingToDisplayForm() {
    Instant windowStart = Instant.parse("2026-08-13T10:00:00Z");
    Instant windowEnd = windowStart.plus(Duration.ofHours(1));

    ExecutionTimeseriesResponse response = ExecutionTimeseriesResponse.from(
            List.of(new RunTimeseriesBucket(windowStart, "CANCELLED", 1L)),
            windowStart, windowEnd, Duration.ofHours(1));

    assertThat(response.buckets().get(0).statusCounts()).containsKey("Cancelled");
    assertThat(response.buckets().get(0).statusCounts()).doesNotContainKey("CANCELLED");
  }
}
