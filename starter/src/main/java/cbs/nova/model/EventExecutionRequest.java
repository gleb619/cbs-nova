package cbs.nova.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record EventExecutionRequest(
    @NotBlank String workflowCode,
    @NotBlank String eventCode,
    @NotBlank String performedBy,
    Map<String, Object> parameters) {

}
