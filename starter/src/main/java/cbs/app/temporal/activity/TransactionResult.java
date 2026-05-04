package cbs.app.temporal.activity;

//TODO: We need a code generation based on some abstract service, input/output pojos must be reused
@Deprecated(forRemoval = true)
public record TransactionResult(boolean success, String errorMessage) {}
