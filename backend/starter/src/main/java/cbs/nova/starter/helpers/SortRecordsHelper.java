package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.SortRecordsIn;
import cbs.nova.starter.helpers.model.SortRecordsOut;
import org.jspecify.annotations.NonNull;

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
 * <li>When both values implement {@link Comparable} and share the same runtime type, natural
 * ordering is used.</li>
 * <li>Otherwise values are coerced to {@link String#valueOf(Object)} and compared
 * lexicographically.</li>
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
            SortRecordsHelper::compareValues);
    if (!input.ascending()) {
      comparator = comparator.reversed();
    }
    sorted.sort(comparator);

    return Result.success(new SortRecordsOut(sorted));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareValues(Object a, Object b) {
    if (a == null && b == null) {
      return 0;
    }
    if (a == null) {
      return 1;
    }
    if (b == null) {
      return -1;
    }
    if (a.getClass() == b.getClass() && a instanceof Comparable) {
      return ((Comparable) a).compareTo(b);
    }
    return String.valueOf(a).compareTo(String.valueOf(b));
  }
}
