package cbs.nova.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import lombok.Builder;

@Builder(toBuilder = true)
public record EventExecutionRequest(
    @NotBlank String workflowCode,
    @NotBlank String eventCode,
    @NotBlank String performedBy,
    Map<String, Object> parameters) {

}
