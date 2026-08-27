package cbs.nova.starter.services.introspection.model;

import java.util.Map;

public record TransactionDetail(
        String name,
        String version,
        String taskQueue,
        String inputType,
        String outputType,
        boolean hasCompensation,
        long startToCloseTimeoutMs,
        Map<String, Object> inputSchema) {
}
