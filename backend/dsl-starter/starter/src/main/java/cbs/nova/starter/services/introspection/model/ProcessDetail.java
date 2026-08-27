package cbs.nova.starter.services.introspection.model;

import java.util.Map;

public record ProcessDetail(
        String name,
        String version,
        String taskQueue,
        String inputType,
        String outputType,
        boolean hasCompensation,
        Map<String, Object> inputSchema) {
}
