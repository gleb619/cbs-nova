package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record SumValuesIn(List<Number> values, @Nullable String operation) {

  public Operation effectiveOperation() {
    if (operation == null || operation.isBlank()) {
      return Operation.ADD;
    }
    try {
      return Operation.valueOf(operation.toUpperCase());
    } catch (IllegalArgumentException e) {
      return Operation.ADD;
    }
  }

  public enum Operation {
    ADD, SUBTRACT, MULTIPLY, DIVIDE, MIN, MAX
  }
}
