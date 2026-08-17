package cbs.nova.starter.helpers.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record SumValuesIn(List<Number> values, @Nullable String operation) {

  public SumValuesIn(List<Number> values) {
    this(values, Operation.ADD.name());
  }

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
