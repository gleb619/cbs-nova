package cbs.app.temporal.massop;

// TODO: remove
@Deprecated(forRemoval = true)
public record MassOpResult(
    Long executionId, String status, long totalItems, long successCount, long failureCount) {}
