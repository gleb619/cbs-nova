package cbs.app.temporal.massop;

import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemStatus;

//TODO: remove
@Deprecated(forRemoval = true)
public record MassOpItemPersistInput(
    MassOperationExecutionEntity execution,
    String itemKey,
    String itemDataJson,
    MassOperationItemStatus status,
    String errorMessage) {}
