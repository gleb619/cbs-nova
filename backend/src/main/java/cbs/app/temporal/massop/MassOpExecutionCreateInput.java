package cbs.app.temporal.massop;

import cbs.nova.entity.MassOperationStatus;

public record MassOpExecutionCreateInput(
    String massOpCode,
    String category,
    String dslVersion,
    MassOperationStatus status,
    String contextJson,
    long totalItems,
    String performedBy,
    String temporalWorkflowId) {}
