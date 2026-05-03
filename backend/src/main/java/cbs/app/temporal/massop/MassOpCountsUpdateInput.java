package cbs.app.temporal.massop;

import cbs.nova.entity.MassOperationExecutionEntity;

public record MassOpCountsUpdateInput(
    MassOperationExecutionEntity execution, long successCount, long failureCount) {

}
