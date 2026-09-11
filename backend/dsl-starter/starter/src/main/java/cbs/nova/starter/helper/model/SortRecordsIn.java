package cbs.nova.starter.helper.model;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public final class SortRecordsIn {

  private final List<Map<String, Object>> records;
  private final String field;
  private final boolean ascending;
  private final @Nullable String algorithm;
  private final @Nullable String direction;

  public List<Map<String, Object>> records() {
    return records;
  }

  public String field() {
    return field;
  }

  public boolean ascending() {
    return ascending;
  }

  public @Nullable String algorithm() {
    return algorithm;
  }

  public @Nullable String direction() {
    return direction;
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
