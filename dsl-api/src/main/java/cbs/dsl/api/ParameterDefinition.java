package cbs.dsl.api;

import lombok.Builder;

@Builder
public record ParameterDefinition(String name, ParameterType type) {
  public static ParameterDefinition required(String name, ParameterType type) {
    return new ParameterDefinition(name, type);
  }

  public static ParameterDefinition optional(String name, ParameterType type) {
    return new ParameterDefinition(name, type);
  }

  public enum ParameterType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN
  }

}