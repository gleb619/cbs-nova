package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.ListOpsIn;
import cbs.nova.starter.helper.model.ListOpsOut;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Performs common list/record operations.
 *
 * <p>
 * The helper supports eight modes (case-insensitive):
 * <ul>
 * <li>{@code "pluck"}: extracts {@code field} from each record in {@code records}, in order.
 * Missing key on any record yields an {@link IllegalArgumentException} naming the record's
 * index.</li>
 * <li>{@code "flatten"}: flattens {@code nested} to the requested {@code depth} (defaults to
 * {@code 1}; {@code -1} means fully flatten). Scalar values pass through unchanged at every level —
 * only nested lists are recursed into. This is intentionally lenient: mixed scalar/list input is
 * accepted without error.</li>
 * <li>{@code "distinct"}: dedupes {@code list} by {@code equals()}, preserving first-seen insertion
 * order via a {@link LinkedHashSet}.</li>
 * <li>{@code "groupBy"}: groups {@code records} by {@code field}, preserving first-seen group
 * insertion order. Missing key on a record yields an {@link IllegalArgumentException} naming the
 * record's index.</li>
 * <li>{@code "countBy"}: counts occurrences of each distinct {@code field} value across
 * {@code records}, first-seen order. Missing key yields an {@link IllegalArgumentException} naming
 * the index.</li>
 * <li>{@code "sumBy"}: numeric sum of {@code field} across {@code records}. Non-numeric values and
 * empty input both yield an {@link IllegalArgumentException}.</li>
 * <li>{@code "minBy"}: the record whose {@code field} is numerically smallest (ties broken by first
 * occurrence). Non-numeric values and empty input both yield an
 * {@link IllegalArgumentException}.</li>
 * <li>{@code "maxBy"}: the record whose {@code field} is numerically largest, same rules as
 * {@code minBy}.</li>
 * </ul>
 *
 * <p>
 * Required inputs that are missing or blank (where required) yield an
 * {@link IllegalArgumentException} naming the missing argument. Unknown {@code mode} also yields an
 * {@link IllegalArgumentException}.
 */
@Helper(name = "listOps")
public class ListOpsHelper implements Executable<ListOpsIn, ListOpsOut> {

  @Override
  public @NonNull Result<ListOpsOut> execute(@NonNull Context<ListOpsIn> ctx) {
    try {
      ListOpsIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "pluck" -> pluck(input.records(), requireField(input.field()));
        case "flatten" -> flatten(input.nested(), input.depth());
        case "distinct" -> distinct(input.list());
        case "groupby" -> groupBy(input.records(), requireField(input.field()));
        case "countby" -> countBy(input.records(), requireField(input.field()));
        case "sumby" -> sumBy(input.records(), requireField(input.field()));
        case "minby" -> minBy(input.records(), requireField(input.field()));
        case "maxby" -> maxBy(input.records(), requireField(input.field()));
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "listOps.mode must be one of pluck, flatten, distinct, groupBy,"
                                + " countBy, sumBy, minBy, maxBy, was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static @NonNull Result<ListOpsOut> pluck(
          List<Map<String, Object>> records, String field) {
    if (records == null) {
      return Result.failure(new IllegalArgumentException("listOps.records is required"));
    }
    List<Object> values = new ArrayList<>(records.size());
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> record = records.get(i);
      if (record == null || !record.containsKey(field)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.pluck: missing field '"
                                + field
                                + "' at record index "
                                + i));
      }
      values.add(record.get(field));
    }
    return Result.success(new ListOpsOut(values));
  }

  private static @NonNull Result<ListOpsOut> flatten(List<Object> nested, Integer depth) {
    if (nested == null) {
      return Result.failure(new IllegalArgumentException("listOps.nested is required"));
    }
    int effectiveDepth = (depth == null) ? 1 : depth;
    List<Object> result = new ArrayList<>();
    flattenInto(nested, effectiveDepth, result);
    return Result.success(new ListOpsOut(result));
  }

  private static void flattenInto(List<Object> source, int remainingDepth, List<Object> sink) {
    for (Object element : source) {
      if (element instanceof List<?> childList && remainingDepth != 0) {
        int nextDepth = (remainingDepth == -1) ? -1 : remainingDepth - 1;
        flattenInto(toObjectList(childList), nextDepth, sink);
      } else {
        sink.add(element);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Object> toObjectList(List<?> source) {
    List<Object> copy = new ArrayList<>(source.size());
    for (Object element : source) {
      copy.add(element);
    }
    return copy;
  }

  private static @NonNull Result<ListOpsOut> distinct(List<Object> list) {
    if (list == null) {
      return Result.failure(new IllegalArgumentException("listOps.list is required"));
    }
    LinkedHashSet<Object> unique = new LinkedHashSet<>(list);
    return Result.success(new ListOpsOut(new ArrayList<>(unique)));
  }

  private static @NonNull Result<ListOpsOut> groupBy(
          List<Map<String, Object>> records, String field) {
    if (records == null) {
      return Result.failure(new IllegalArgumentException("listOps.records is required"));
    }
    LinkedHashMap<Object, List<Map<String, Object>>> groups = new LinkedHashMap<>();
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> record = records.get(i);
      if (record == null || !record.containsKey(field)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.groupBy: missing field '"
                                + field
                                + "' at record index "
                                + i));
      }
      groups.computeIfAbsent(record.get(field), k -> new ArrayList<>()).add(record);
    }
    return Result.success(new ListOpsOut(groups));
  }

  private static @NonNull Result<ListOpsOut> countBy(
          List<Map<String, Object>> records, String field) {
    if (records == null) {
      return Result.failure(new IllegalArgumentException("listOps.records is required"));
    }
    LinkedHashMap<Object, Long> counts = new LinkedHashMap<>();
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> record = records.get(i);
      if (record == null || !record.containsKey(field)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.countBy: missing field '"
                                + field
                                + "' at record index "
                                + i));
      }
      counts.merge(record.get(field), 1L, Long::sum);
    }
    return Result.success(new ListOpsOut(counts));
  }

  private static @NonNull Result<ListOpsOut> sumBy(
          List<Map<String, Object>> records, String field) {
    if (records == null || records.isEmpty()) {
      return Result.failure(new IllegalArgumentException("listOps.sumBy: records is empty"));
    }
    double sum = 0.0;
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> record = records.get(i);
      if (record == null || !record.containsKey(field)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.sumBy: missing field '"
                                + field
                                + "' at record index "
                                + i));
      }
      Object value = record.get(field);
      if (!(value instanceof Number number)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.sumBy: non-numeric value at record index "
                                + i
                                + ": "
                                + preview(value)));
      }
      sum += number.doubleValue();
    }
    return Result.success(new ListOpsOut(sum));
  }

  private static @NonNull Result<ListOpsOut> minBy(
          List<Map<String, Object>> records, String field) {
    if (records == null || records.isEmpty()) {
      return Result.failure(new IllegalArgumentException("listOps.minBy: records is empty"));
    }
    Map<String, Object> best = null;
    double bestValue = Double.NaN;
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> record = records.get(i);
      if (record == null || !record.containsKey(field)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.minBy: missing field '"
                                + field
                                + "' at record index "
                                + i));
      }
      Object value = record.get(field);
      if (!(value instanceof Number number)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.minBy: non-numeric value at record index "
                                + i
                                + ": "
                                + preview(value)));
      }
      double candidate = number.doubleValue();
      if (best == null || candidate < bestValue) {
        best = record;
        bestValue = candidate;
      }
    }
    return Result.success(new ListOpsOut(best));
  }

  private static @NonNull Result<ListOpsOut> maxBy(
          List<Map<String, Object>> records, String field) {
    if (records == null || records.isEmpty()) {
      return Result.failure(new IllegalArgumentException("listOps.maxBy: records is empty"));
    }
    Map<String, Object> best = null;
    double bestValue = Double.NaN;
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> record = records.get(i);
      if (record == null || !record.containsKey(field)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.maxBy: missing field '"
                                + field
                                + "' at record index "
                                + i));
      }
      Object value = record.get(field);
      if (!(value instanceof Number number)) {
        return Result.failure(
                new IllegalArgumentException(
                        "listOps.maxBy: non-numeric value at record index "
                                + i
                                + ": "
                                + preview(value)));
      }
      double candidate = number.doubleValue();
      if (best == null || candidate > bestValue) {
        best = record;
        bestValue = candidate;
      }
    }
    return Result.success(new ListOpsOut(best));
  }

  private static @NonNull String requireField(String field) {
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("listOps.field is required");
    }
    return field;
  }

  private static String preview(Object value) {
    if (value == null) {
      return "null";
    }
    String string = String.valueOf(value);
    return string.length() > 40 ? string.substring(0, 40) + "..." : string;
  }
}
