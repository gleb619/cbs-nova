package cbs.app.temporal.massop;

//TODO: remove
@Deprecated(forRemoval = true)
public record MassOpItemInput(
    String itemId,
    String itemDataJson,
    String massOpCode,
    Long massOperationExecutionId,
    String performedBy,
    String dslVersion) {}
