package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.GroupRecordsIn;
import cbs.nova.starter.helper.model.GroupRecordsOut;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Groups a list of records by one or more dot-separated key paths.
 *
 * <p>
 * {@code key} may be a single path string or a list of path strings. Each path is resolved into a
 * value per record; the resulting values form a composite list key. Missing or null values resolve
 * to the sentinel {@code "__null__"} so those records land in a dedicated bucket.
 *
 * <p>
 * Both group order and record order within groups are stable (insertion order).
 */
@Helper(name = "groupRecords")
public class GroupRecordsHelper implements Executable<GroupRecordsIn, GroupRecordsOut> {

  private static final String NULL_KEY = "__null__";

  @Override
  public @NonNull Result<GroupRecordsOut> execute(@NonNull Context<GroupRecordsIn> ctx) {
    GroupRecordsIn input = ctx.body();
    if (input.records() == null || input.records().isEmpty()) {
      return Result.success(new GroupRecordsOut(new LinkedHashMap<>()));
    }

    List<String> paths = input.keyPaths();
    Map<Object, List<Map<String, Object>>> groups = new LinkedHashMap<>();

    for (Map<String, Object> record : input.records()) {
      if (record == null) {
        continue;
      }
      Object compositeKey = (paths.size() == 1)
              ? resolveValue(record, paths.get(0))
              : compositeKey(record, paths);
      groups.computeIfAbsent(compositeKey, k -> new ArrayList<>()).add(record);
    }

    return Result.success(new GroupRecordsOut(Collections.unmodifiableMap(groups)));
  }

  private static List<Object> compositeKey(Map<String, Object> record, List<String> paths) {
    List<Object> key = new ArrayList<>(paths.size());
    for (String path : paths) {
      key.add(resolveValue(record, path));
    }
    return Collections.unmodifiableList(key);
  }

  private static Object resolveValue(Map<String, Object> record, String path) {
    if (path == null || path.isBlank()) {
      return NULL_KEY;
    }
    Object current = record;
    for (String segment : path.split("\\.")) {
      if (segment.isBlank()) {
        continue;
      }
      if (current instanceof Map<?, ?> map) {
        current = map.get(segment);
      } else {
        return NULL_KEY;
      }
      if (current == null) {
        return NULL_KEY;
      }
    }
    return current == null ? NULL_KEY : current;
  }
}
