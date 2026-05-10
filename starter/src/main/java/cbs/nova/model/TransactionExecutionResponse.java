package cbs.nova.model;

import cbs.dsl.api.TransactionTypes.TransactionStatus;
import lombok.Builder;

@Builder(toBuilder = true)
public record TransactionExecutionResponse(String executionId, TransactionStatus status) {}