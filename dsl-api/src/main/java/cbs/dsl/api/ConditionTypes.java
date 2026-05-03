package cbs.dsl.api;

import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consolidated condition DSL types.
 *
 * <p>ConditionInput carries the raw parameters and event metadata for evaluating a condition.
 * ConditionOutput wraps the boolean result of the evaluation.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConditionTypes {

  /** ConditionInput carries the raw parameters and event metadata for evaluating a condition. */
  @Json
  @Builder(toBuilder = true)
  public record ConditionInput(Map<String, Object> params, String eventCode, Long eventNumber)
      implements ConditionFunction.ConditionArg {

    @Override
    public Map<String, Object> toMap() {
      return params;
    }

    /**
     * Returns parameters with nulls filtered out, suitable for TransactionContext.
     *
     * @return map with null values excluded
     */
    public Map<String, Object> nonNullParams() {
      return params.entrySet().stream()
          .filter(e -> e.getValue() != null)
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
  }

  /** ConditionOutput wraps the boolean result of the evaluation. */
  @Json
  @Builder(toBuilder = true)
  public record ConditionOutput(boolean result) implements ConditionFunction.ConditionResult {
    @Override
    public boolean getValue() {
      return result;
    }
  }
}
