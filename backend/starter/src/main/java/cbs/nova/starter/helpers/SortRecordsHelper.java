package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.SortRecordsIn;
import cbs.nova.starter.helpers.model.SortRecordsOut;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Sorts a list of records (maps) by a named field.
 *
 * <p>
 * Ordering rules:
 * <ul>
 * <li>Records are sorted in place using a stable JDK sort.</li>
 * <li>Null/missing field values are sorted to the end (nulls last).</li>
 * <li>The {@code algorithm} parameter selects the comparison strategy: {@code natural} (default),
 * {@code string}, or {@code numeric}.</li>
 * <li>The {@code direction} parameter (or legacy {@code ascending} boolean) selects
 * ascending/descending order.</li>
 * </ul>
 */
@Helper(name = "sortRecords")
public class SortRecordsHelper implements Executable<SortRecordsIn, SortRecordsOut> {

  @Override
  public @NonNull Result<SortRecordsOut> execute(@NonNull Context<SortRecordsIn> ctx) {
    SortRecordsIn input = ctx.body();
    if (input.records() == null || input.records().isEmpty()) {
      return Result.success(new SortRecordsOut(List.of()));
    }

    List<Map<String, Object>> sorted = new ArrayList<>(input.records());
    Comparator<Map<String, Object>> comparator = Comparator.comparing(
            (Map<String, Object> record) -> record.get(input.field()),
            (a, b) -> compareValues(a, b, input.effectiveAlgorithm()));
    if ("desc".equals(input.effectiveDirection())) {
      comparator = comparator.reversed();
    }
    sorted.sort(comparator);

    return Result.success(new SortRecordsOut(sorted));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareValues(Object a, Object b, String algorithm) {
    if (a == null && b == null) {
      return 0;
    }
    if (a == null) {
      return 1;
    }
    if (b == null) {
      return -1;
    }
    return switch (algorithm) {
      case "string" -> String.valueOf(a).compareTo(String.valueOf(b));
      case "numeric" -> toBigDecimal(a).compareTo(toBigDecimal(b));
      default -> naturalCompare(a, b);
    };
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int naturalCompare(Object a, Object b) {
    if (a.getClass() == b.getClass() && a instanceof Comparable) {
      return ((Comparable) a).compareTo(b);
    }
    return String.valueOf(a).compareTo(String.valueOf(b));
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    return new BigDecimal(String.valueOf(value));
  }
}
