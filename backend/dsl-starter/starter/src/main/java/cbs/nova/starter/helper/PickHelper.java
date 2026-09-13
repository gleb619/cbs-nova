package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.PickIn;
import cbs.nova.starter.helper.model.PickOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Projects a {@code Map} down to ({@code pick} mode) or minus ({@code omit} mode) a list of keys —
 * useful for shaping outbound {@code httpCall} / webhook payloads.
 *
 * <p>
 * Two modes (case-insensitive):
 * <ul>
 * <li>{@code "pick"} (default when {@code mode} is {@code null}/blank): keeps only the listed keys.
 * The result contains each listed key that is present in {@code source}, in {@code keys} order;
 * keys absent from {@code source} are silently skipped (not null-filled). A key that is present
 * with a {@code null} value is kept — {@code null} is a value, not absence.</li>
 * <li>{@code "omit"}: returns a copy of {@code source} (insertion order preserved) with every
 * listed key removed, including present-but-null ones.</li>
 * </ul>
 *
 * <p>
 * Only flat, top-level keys are supported; dotted paths ({@code "a.b"}) are a follow-up. Nested
 * values are copied by reference (shallow copy) — deep projection is a follow-up. The input
 * {@code source} map is never mutated.
 */
@Helper(name = "pick")
public class PickHelper implements Executable<PickIn, PickOut> {

  @Override
  public @NonNull Result<PickOut> execute(@NonNull Context<PickIn> ctx) {
    try {
      PickIn input = ctx.body();
      Map<String, Object> source = input.source();
      if (source == null) {
        return Result.failure(new IllegalArgumentException("pick.source is required"));
      }
      List<String> keys = input.keys();
      if (keys == null || keys.isEmpty()) {
        return Result.failure(new IllegalArgumentException("pick.keys must be non-empty"));
      }
      String mode = (input.mode() == null || input.mode().isBlank())
              ? "pick"
              : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "pick" -> Result.success(new PickOut(pick(source, keys)));
        case "omit" -> Result.success(new PickOut(omit(source, keys)));
        default -> Result.failure(new IllegalArgumentException(
                "pick.mode must be one of pick, omit, was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  // --- mode: pick ------------------------------------------------------

  private static Map<String, Object> pick(Map<String, Object> source, List<String> keys) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : keys) {
      // containsKey, not a null check: a present-but-null value must be kept.
      if (source.containsKey(key)) {
        result.put(key, source.get(key));
      }
    }
    return result;
  }

  // --- mode: omit ------------------------------------------------------

  private static Map<String, Object> omit(Map<String, Object> source, List<String> keys) {
    Map<String, Object> result = new LinkedHashMap<>(source);
    for (String key : keys) {
      result.remove(key);
    }
    return result;
  }
}
