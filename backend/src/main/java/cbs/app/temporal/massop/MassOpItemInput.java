package cbs.app.temporal.massop;

public record MassOpItemInput(
    String itemId,
    String itemDataJson,
    String massOpCode,
    Long massOperationExecutionId,
    String performedBy,
    String dslVersion) {

}
