package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record SortRecordsIn(
        List<Map<String, Object>> records,
        String field,
        boolean ascending,
        @Nullable String algorithm,
        @Nullable String direction) {

  public SortRecordsIn(List<Map<String, Object>> records, String field, boolean ascending) {
    this(records, field, ascending, null, null);
  }

  public SortRecordsIn(List<Map<String, Object>> records, String field) {
    this(records, field, true, null, null);
  }

  public String effectiveAlgorithm() {
    return algorithm == null || algorithm.isBlank() ? "natural" : algorithm.toLowerCase();
  }

  public String effectiveDirection() {
    if (direction != null && !direction.isBlank()) {
      return direction.toLowerCase();
    }
    return ascending ? "asc" : "desc";
  }
}
