package cbs.dsl.api;

import cbs.dsl.api.HelperFunction.HelperArg;
import cbs.dsl.api.HelperFunction.HelperResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Consolidated helper DSL types. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HelperTypes {

  @Json
  @Builder(toBuilder = true)
  public record HelperInput(Map<String, Object> params, String eventCode, Long workflowExecutionId)
      implements HelperArg {

    @Override
    public Map<String, Object> toMap() {
      return params;
    }
  }

  @Json
  @Builder(toBuilder = true)
  public record HelperOutput(Object value) implements HelperResult {

    @Override
    public Map<String, Object> toMap() {
      return Map.of("value", value);
    }
  }
}
