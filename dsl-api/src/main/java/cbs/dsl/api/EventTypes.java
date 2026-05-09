package cbs.dsl.api;

import cbs.dsl.api.EventFunction.EventArg;
import cbs.dsl.api.EventFunction.EventResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Consolidated event DSL types. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventTypes {

  @Json
  @Builder(toBuilder = true)
  public record EventInput(
      Map<String, Object> params, String eventCode, Long eventNumber, String workflowExecutionId)
      implements EventArg {}

  @Json
  @Builder(toBuilder = true)
  public record EventOutput(Map<String, Object> params, String status) implements EventResult {

    public EventOutput(Map<String, Object> result) {
      this(result, "SUCCESS");
    }
  }
}
