package cbs.app.temporal.activity;

public record TransactionActivityInput(
    String transactionCode,
    String contextJson,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion) {}
