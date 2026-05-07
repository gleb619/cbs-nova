package cbs.app.temporal.massop;

import cbs.nova.entity.MassOperationExecutionEntity;

//TODO: remove
@Deprecated(forRemoval = true)
public record MassOpCountsUpdateInput(
    MassOperationExecutionEntity execution, long successCount, long failureCount) {}
