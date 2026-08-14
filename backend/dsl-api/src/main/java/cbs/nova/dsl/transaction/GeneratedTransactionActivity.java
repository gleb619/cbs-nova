package cbs.nova.dsl.transaction;

public interface GeneratedTransactionActivity<T> {

  Object execute(DslTemporalTransactionRequest<T> request);

}
