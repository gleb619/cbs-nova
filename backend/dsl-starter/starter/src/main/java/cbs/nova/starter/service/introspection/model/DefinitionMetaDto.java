package cbs.nova.starter.service.introspection.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public record DefinitionMetaDto(
        String name,
        String type,
        String version,
        String taskQueue,
        String inputType,
        String outputType,
        Boolean hasCompensation,
        String description,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> inputSchema) {
}
