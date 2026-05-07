package cbs.nova.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.Map;

// TODO: remove file, instead use another api
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record EventExecutionRequest(
    @NotBlank String workflowCode,
    @NotBlank String eventCode,
    @NotBlank String performedBy,
    Map<String, Object> parameters) {}
