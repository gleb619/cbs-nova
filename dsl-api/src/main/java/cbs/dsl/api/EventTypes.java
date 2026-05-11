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
  public record EventInput(String eventNumber, Map<String, Object> params) implements EventArg {}

  @Json
  @Builder(toBuilder = true)
  public record EventOutput(Map<String, Object> params, EventStatus status) implements EventResult {

    public static EventOutput success(Map<String, Object> result) {
      return new EventOutput(result, EventStatus.SUCCESS);
    }
  }

  public enum EventStatus {
    UNDEFINED,
    SUCCESS,
    PENDING,
    ERROR,
  }
}
