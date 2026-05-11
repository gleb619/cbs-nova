package cbs.dsl.api;

import cbs.dsl.api.ParametersFunction.ParametersArg;
import cbs.dsl.api.ParametersFunction.ParametersResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/** Consolidated Parameters DSL types. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ParametersTypes {

  @Json
  // TODO: Delegate paramer evaluation to
  // `dsl-api/src/main/java/cbs/dsl/evaluator/RegistryParameterEvaluator.java`
  //  move logic from pojo to a specific Evaluator instead
  @Deprecated
  public record ParametersInput(Map<String, Object> params) implements ParametersArg {

    public static ParametersInput from(Map<String, Object> input) {
      return new ParametersInput(input);
    }

    public ParametersOutput asOutput() {
      return new ParametersOutput(params);
    }

    public boolean isString(String key) {
      return params.containsKey(key) && params.get(key) instanceof String;
    }

    public boolean isNumber(String key) {
      return params.containsKey(key) && params.get(key) instanceof Number;
    }

    public boolean isDecimal(String key) {
      return params.containsKey(key)
          && (params.get(key) instanceof BigDecimal
              || params.get(key) instanceof Double
              || params.get(key) instanceof Float);
    }

    public boolean isBoolean(String key) {
      return params.containsKey(key) && params.get(key) instanceof Boolean;
    }

    public boolean isPresent(String key) {
      return params.containsKey(key) && params.get(key) != null;
    }

    public boolean isNull(String key) {
      return !params.containsKey(key) || params.get(key) == null;
    }

    public Object get(String key) {
      return params.get(key);
    }

    public String getString(String key) {
      return isString(key) ? (String) params.get(key) : null;
    }

    public Number getNumber(String key) {
      return isNumber(key) ? (Number) params.get(key) : null;
    }

    public BigDecimal getDecimal(String key) {
      if (!isDecimal(key)) return null;
      Object val = params.get(key);
      if (val instanceof BigDecimal) return (BigDecimal) val;
      if (val instanceof Double) return BigDecimal.valueOf((Double) val);
      if (val instanceof Float) return BigDecimal.valueOf((Float) val);
      if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
      return null;
    }

    public Boolean getBoolean(String key) {
      return isBoolean(key) ? (Boolean) params.get(key) : null;
    }
  }

  @Json
  // TODO: remove record
  @Deprecated(forRemoval = true)
  public record ParametersOutput(Map<String, Object> params) implements ParametersResult {

    public static ParametersOutput from(Map<String, Object> params) {
      return new ParametersOutput(params);
    }
  }

  @Deprecated(forRemoval = true)
  public record ParameterError(String name, String code, String message) {

    public static ParameterError missing(String name) {
      return new ParameterError(
          name, "parameter.missing", "Parameter '%s' is required".formatted(name));
    }

    public static ParameterError nonString(String name) {
      return new ParameterError(
          name, "parameter.non-string", "Parameter '%s' must be a string".formatted(name));
    }

    public static ParameterError nonNumber(String name) {
      return new ParameterError(
          name, "parameter.non-number", "Parameter '%s' must be a number".formatted(name));
    }

    public static ParameterError nonDecimal(String name) {
      return new ParameterError(
          name, "parameter.non-decimal", "Parameter '%s' must be a decimal".formatted(name));
    }

    public static ParameterError nonBoolean(String name) {
      return new ParameterError(
          name, "parameter.non-boolean", "Parameter '%s' must be a boolean".formatted(name));
    }

    public static ParameterError unexpected(String name) {
      return new ParameterError(
          name, "parameter.unexpected", "Parameter '%s' is not expected".formatted(name));
    }

    public static ParameterError typeMismatch(String name, String expectedType) {
      return new ParameterError(
          name,
          "parameter.type-mismatch",
          "Parameter '%s' must be of type %s".formatted(name, expectedType));
    }
  }
}
