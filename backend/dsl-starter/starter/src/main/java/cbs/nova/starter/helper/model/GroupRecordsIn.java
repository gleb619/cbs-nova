package cbs.nova.starter.helper.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code groupRecords} helper.
 *
 * <p>
 * {@code key} is either a single dot-separated path (String) or a list of dot-separated paths
 * (List<String>). Each path is resolved against a record to build a composite grouping key. A
 * missing or null component resolves to the sentinel {@code "__null__"}.
 */
public record GroupRecordsIn(List<Map<String, Object>> records, @Nullable Object key) {

  public List<String> keyPaths() {
    if (key == null) {
      return List.of();
    }
    if (key instanceof String s) {
      return s.isBlank() ? List.of() : List.of(s);
    }
    if (key instanceof List<?> list) {
      List<String> paths = new ArrayList<>(list.size());
      for (Object element : list) {
        String path = (element == null) ? "" : element.toString();
        if (!path.isBlank()) {
          paths.add(path);
        }
      }
      return Collections.unmodifiableList(paths);
    }
    String s = key.toString();
    return s.isBlank() ? List.of() : List.of(s);
  }
}
