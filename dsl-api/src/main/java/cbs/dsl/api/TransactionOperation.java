package cbs.dsl.api;

import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;

@FunctionalInterface
public interface TransactionOperation {

  TransactionOutput execute(TransactionInput input);
}
