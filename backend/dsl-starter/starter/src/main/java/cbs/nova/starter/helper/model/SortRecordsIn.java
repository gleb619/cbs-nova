package cbs.nova.starter.helper.model;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public record SortRecordsIn(
        List<Map<String, Object>> records,
        String field,
        boolean ascending,
        @Nullable String algorithm,
        @Nullable String direction) {

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
