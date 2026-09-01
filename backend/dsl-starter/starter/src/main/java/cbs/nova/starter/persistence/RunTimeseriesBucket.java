package cbs.nova.starter.persistence;

import java.time.Instant;

/**
 * One (bucket, status) row of the run time-series: how many runs started in {@code bucketStart}'s
 * window and ended up in {@code status}. Narrow rows (one per status, not a wide
 * {@code statusCounts} map per bucket) because the SQL
 * {@code GROUP BY date_trunc('minute', started_at), status} shape is a single scan that the
 * {@code (status, started_at)} index serves efficiently; the handler zero-fills missing (bucket,
 * status) pairs in Java so the dashboard x-axis stays uniform.
 *
 * @param bucketStart
 *          start of the time bucket (UTC, aligned on {@code bucketSize} boundaries)
 * @param status
 *          raw stored status name (e.g. {@code RUNNING}); the handler maps this to display casing
 * @param count
 *          number of runs whose {@code started_at} fell in this bucket and whose final status
 *          matches {@code status}
 */
public record RunTimeseriesBucket(Instant bucketStart, String status, long count) {
}
