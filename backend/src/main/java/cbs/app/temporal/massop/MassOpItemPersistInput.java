package cbs.app.temporal.massop;

import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemStatus;

public record MassOpItemPersistInput(
    MassOperationExecutionEntity execution,
    String itemKey,
    String itemDataJson,
    MassOperationItemStatus status,
    String errorMessage) {}
