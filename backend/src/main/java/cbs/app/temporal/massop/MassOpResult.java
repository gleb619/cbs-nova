package cbs.app.temporal.massop;

public record MassOpResult(
    Long executionId, String status, long totalItems, long successCount, long failureCount) {}
