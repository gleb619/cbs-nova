package cbs.nova.model;

import cbs.dsl.api.EventTypes.EventInput;
import lombok.Builder;

import java.util.Map;
import org.jspecify.annotations.NonNull;

@Builder(toBuilder = true)
public record EventExecutionRequest(
    @NonNull String eventCode,
    @NonNull String performedBy,
    @NonNull Map<String, Object> params) {

  public EventInput toEventInput(String eventNumber) {
    return EventInput.builder()
        .eventNumber(eventNumber)
        .params(params())
        .build();
  }

}
