package cbs.nova.model;

import cbs.dsl.api.HelperTypes.HelperInput;
import lombok.Builder;
import org.jspecify.annotations.NonNull;

import java.util.Map;

@Builder(toBuilder = true)
public record HelperExecutionRequest(
    @NonNull String helperCode,
    @NonNull String performedBy,
    @NonNull Map<String, Object> params) {

  public HelperInput toHelperInput() {
    return HelperInput.builder().params(params()).build();
  }
}
