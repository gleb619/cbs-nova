package cbs.nova.starter.model;

public record TransactionExecutionDto(
        String transactionName,
        Object input,
        String executedAt) {
}
