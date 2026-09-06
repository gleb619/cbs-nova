package cbs.nova.starter.model;

public record TransactionExecutionDto(
        String transactionName,
        Object input,
        String executedAt,
        String status,
        String startedAt,
        String finishedAt,
        Long duration,
        String error) {
}
