package cbs.nova.temporal.workflow;

import cbs.dsl.api.TransactionTypes.TransactionInput;
import lombok.Builder;

import java.io.Serializable;

@Builder(toBuilder = true)
public record GenericTransactionRequest(String activityCode, TransactionInput input)
    implements Serializable {}
