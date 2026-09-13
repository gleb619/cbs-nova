package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

public record TraceStepDto(
        String id,
        String stepType,
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isCompensation) {

}
