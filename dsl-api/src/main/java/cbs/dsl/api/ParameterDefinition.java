package cbs.dsl.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines a parameter with name, type, and optional flag.
 */
@Getter
@RequiredArgsConstructor
public abstract class ParameterDefinition {

  public final String name;
  public final ParameterType type;
  public final Boolean required;

  public static ParameterDefinition mandatory(String name, ParameterType type) {
    return new ParameterDefinition(name, type, true) {};
  }

  public static ParameterDefinition optional(String name, ParameterType type) {
    return new ParameterDefinition(name, type, false) {};
  }

  public enum ParameterType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN
  }

}