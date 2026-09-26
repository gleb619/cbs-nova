package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.MergeRecordsIn;
import cbs.nova.starter.helper.model.MergeRecordsOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Merges two or more {@code Map<String, Object>} records.
 *
 * <p>
 * Two modes are supported (case-insensitive):
 * <ul>
 * <li>{@code "shallow"} (default): later records overwrite earlier records key-by-key at the top
 * level only. A single record returns an equivalent map.</li>
 * <li>{@code "deep"}: nested maps are merged recursively key-by-key; non-map values are overwritten
 * (last-write-wins) and lists are replaced rather than concatenated. When a key exists as a map in
 * one record and a scalar in another, the scalar wins.</li>
 * </ul>
 *
 * <p>
 * {@code records} must contain at least one non-null record. Unknown {@code mode} yields an
 * {@link IllegalArgumentException}.
 */
@Helper(name = "mergeRecords")
public class MergeRecordsHelper implements Executable<MergeRecordsIn, MergeRecordsOut> {

  @Override
  public @NonNull Result<MergeRecordsOut> execute(@NonNull Context<MergeRecordsIn> ctx) {
    try {
      MergeRecordsIn input = ctx.body();
      String mode = (input.mode() == null) ? "shallow" : input.mode().toLowerCase(Locale.ROOT);
      List<Map<String, Object>> records = requireRecords(input.records());
      return switch (mode) {
        case "shallow" -> Result.success(new MergeRecordsOut(mergeShallow(records)));
        case "deep" -> Result.success(new MergeRecordsOut(mergeDeep(records)));
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "mergeRecords.mode must be one of shallow, deep, was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static List<Map<String, Object>> requireRecords(List<Map<String, Object>> records) {
    if (records == null || records.isEmpty()) {
      throw new IllegalArgumentException(
              "mergeRecords.records must contain at least one record");
    }
    for (int i = 0; i < records.size(); i++) {
      if (records.get(i) == null) {
        throw new IllegalArgumentException(
                "mergeRecords.records: null record at index " + i);
      }
    }
    return records;
  }

  private static Map<String, Object> mergeShallow(List<Map<String, Object>> records) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map<String, Object> record : records) {
      result.putAll(record);
    }
    return result;
  }

  private static Map<String, Object> mergeDeep(List<Map<String, Object>> records) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map<String, Object> record : records) {
      for (Map.Entry<String, Object> entry : record.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        Object existing = result.get(key);
        if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
          result.put(key, mergeMaps(toStringObjectMap(existingMap), toStringObjectMap(valueMap)));
        } else {
          result.put(key, value);
        }
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mergeMaps(
          Map<String, Object> base, Map<String, Object> overlay) {
    Map<String, Object> merged = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : base.entrySet()) {
      String key = entry.getKey();
      Object baseValue = entry.getValue();
      if (overlay.containsKey(key)) {
        Object overlayValue = overlay.get(key);
        if (baseValue instanceof Map<?, ?> baseMap
                && overlayValue instanceof Map<?, ?> overlayMap) {
          merged.put(key, mergeMaps(toStringObjectMap(baseMap), toStringObjectMap(overlayMap)));
        } else {
          merged.put(key, overlayValue);
        }
      } else {
        merged.put(key, baseValue);
      }
    }
    for (Map.Entry<String, Object> entry : overlay.entrySet()) {
      if (!base.containsKey(entry.getKey())) {
        merged.put(entry.getKey(), entry.getValue());
      }
    }
    return merged;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      copy.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return copy;
  }
}
