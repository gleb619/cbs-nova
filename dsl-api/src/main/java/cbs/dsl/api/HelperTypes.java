package cbs.dsl.api;

import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Consolidated helper DSL types. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HelperTypes {

  @Json
  public record HelperInput(
      Map<String, Object> params, String eventCode, Long workflowExecutionId) {}

  @Json
  public record HelperOutput(Object value) {}
}
