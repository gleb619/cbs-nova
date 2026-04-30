package cbs.dsl.api;

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
      implements HelperFunction.HelperArg {}

  @Json
  @Builder(toBuilder = true)
  public record HelperOutput(Object value) implements HelperFunction.HelperResult {}
}
