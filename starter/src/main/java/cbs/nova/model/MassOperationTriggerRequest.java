package cbs.nova.model;

import jakarta.validation.constraints.NotBlank;

public record MassOperationTriggerRequest(
    @NotBlank String massOpCode,
    @NotBlank String performedBy,
    @NotBlank String dslVersion,
    String contextJson,
    String triggerType,
    String triggerSource) {

}
