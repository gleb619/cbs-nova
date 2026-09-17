package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link RunTimeseriesBucketing#foldMinuteBuckets} folding math: integer-division bucket
 * indexing, per-status count merging, and the output sort order (bucketStart, then status).
 */
class RunTimeseriesBucketingTest {

  private static final Instant WINDOW_START = Instant.parse("2026-01-01T00:00:00Z");
  private static final long FIVE_MINUTES = 300L;

  @Test
  void emptyInputProducesEmptyOutput() {
    assertThat(RunTimeseriesBucketing.foldMinuteBuckets(List.of(), WINDOW_START, FIVE_MINUTES))
            .isEmpty();
  }

  @Test
  void singleMinuteRowProducesSingleBucketUnchanged() {
    RunTimeseriesBucket row = new RunTimeseriesBucket(WINDOW_START.plusSeconds(61), "RUNNING", 3);

    assertThat(RunTimeseriesBucketing.foldMinuteBuckets(List.of(row), WINDOW_START, FIVE_MINUTES))
            .containsExactly(new RunTimeseriesBucket(WINDOW_START, "RUNNING", 3));
  }

  @Test
  void sameBucketSameStatusCountsAreSummed() {
    List<RunTimeseriesBucket> rows = List.of(
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(0), "RUNNING", 1),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(60), "RUNNING", 2),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(299), "RUNNING", 4));

    assertThat(RunTimeseriesBucketing.foldMinuteBuckets(rows, WINDOW_START, FIVE_MINUTES))
            .containsExactly(new RunTimeseriesBucket(WINDOW_START, "RUNNING", 7));
  }

  @Test
  void sameBucketDifferentStatusesProduceSeparateEntries() {
    List<RunTimeseriesBucket> rows = List.of(
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(60), "COMPLETED", 2),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(120), "FAILED", 1));

    assertThat(RunTimeseriesBucketing.foldMinuteBuckets(rows, WINDOW_START, FIVE_MINUTES))
            .containsExactly(
                    new RunTimeseriesBucket(WINDOW_START, "COMPLETED", 2),
                    new RunTimeseriesBucket(WINDOW_START, "FAILED", 1));
  }

  @Test
  void rowsOnEitherSideOfBucketBoundarySplitIntoSeparateBuckets() {
    // 299s = last second of bucket 0, 300s = first second of bucket 1, 301s = second second.
    List<RunTimeseriesBucket> rows = List.of(
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(299), "RUNNING", 1),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(300), "RUNNING", 2),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(301), "RUNNING", 4));

    assertThat(RunTimeseriesBucketing.foldMinuteBuckets(rows, WINDOW_START, FIVE_MINUTES))
            .containsExactly(
                    new RunTimeseriesBucket(WINDOW_START, "RUNNING", 1),
                    new RunTimeseriesBucket(WINDOW_START.plusSeconds(300), "RUNNING", 6));
  }

  @Test
  void outputIsSortedByBucketStartThenStatus() {
    List<RunTimeseriesBucket> rows = List.of(
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(360), "RUNNING", 1),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(10), "FAILED", 2),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(370), "FAILED", 3),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(10), "COMPLETED", 4),
            new RunTimeseriesBucket(WINDOW_START.plusSeconds(720), "COMPLETED", 5));

    assertThat(RunTimeseriesBucketing.foldMinuteBuckets(rows, WINDOW_START, FIVE_MINUTES))
            .containsExactly(
                    new RunTimeseriesBucket(WINDOW_START, "COMPLETED", 4),
                    new RunTimeseriesBucket(WINDOW_START, "FAILED", 2),
                    new RunTimeseriesBucket(WINDOW_START.plusSeconds(300), "FAILED", 3),
                    new RunTimeseriesBucket(WINDOW_START.plusSeconds(300), "RUNNING", 1),
                    new RunTimeseriesBucket(WINDOW_START.plusSeconds(600), "COMPLETED", 5));
  }

  @Test
  void rowBeforeWindowStartTruncatesTowardZeroBucketZero() {
    // Java long division truncates toward zero, so a row 1s before windowStart with 300s buckets
    // lands in bucketIndex 0 (NOT floor bucket -1); its seconds are silently absorbed into
    // bucket 0 rather than shifted to a bucket before windowStart.
    RunTimeseriesBucket row = new RunTimeseriesBucket(WINDOW_START.minusSeconds(1), "RUNNING", 1);

    assertThat(RunTimeseriesBucketing.foldMinuteBuckets(List.of(row), WINDOW_START, FIVE_MINUTES))
            .containsExactly(new RunTimeseriesBucket(WINDOW_START, "RUNNING", 1));
  }

  @Test
  void rowExactlyOneFullBucketBeforeWindowStartKeepsNegativeIndex() {
    // -300s / 300s = -1 exactly (no truncation), so the row gets bucketStart = windowStart - 300s
    // and sorts before bucket 0.
    RunTimeseriesBucket row = new RunTimeseriesBucket(WINDOW_START.minusSeconds(300), "RUNNING", 1);
    RunTimeseriesBucket after = new RunTimeseriesBucket(WINDOW_START.plusSeconds(60), "RUNNING", 2);

    assertThat(RunTimeseriesBucketing.foldMinuteBuckets(List.of(after, row), WINDOW_START,
            FIVE_MINUTES))
            .containsExactly(
                    new RunTimeseriesBucket(WINDOW_START.minusSeconds(300), "RUNNING", 1),
                    new RunTimeseriesBucket(WINDOW_START, "RUNNING", 2));
  }
}
