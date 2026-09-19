package cbs.nova.starter.vhs.loadtest;

import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Sorted-array percentile computation — reuses the same approach as the existing {@code cbs_cli}
 * {@code LoadtestCommand} but as a standalone helper.
 */
public final class VhsPercentiles {

  private VhsPercentiles() {
  }

  /**
   * Compute percentiles from a sorted (ascending) list of values.
   *
   * @param sortedValues
   *          ascending latency values (milliseconds)
   * @param ps
   *          percentile values (e.g. 50, 95, 99)
   * @return one result per requested percentile, in the same order
   */
  public static long[] compute(@NonNull List<Long> sortedValues, int... ps) {
    int total = sortedValues.size();
    if (total == 0) {
      long[] result = new long[ps.length];
      return result;
    }
    long[] result = new long[ps.length];
    for (int i = 0; i < ps.length; i++) {
      int idx = (int) (total * ps[i] / 100L);
      idx = Math.max(0, Math.min(idx, total - 1));
      result[i] = sortedValues.get(idx);
    }
    return result;
  }

  /**
   * Compute percentiles from raw values — sorts first.
   */
  public static long[] computeUnsorted(@NonNull List<Long> rawValues, int... ps) {
    return compute(rawValues.stream().sorted().toList(), ps);
  }
}
