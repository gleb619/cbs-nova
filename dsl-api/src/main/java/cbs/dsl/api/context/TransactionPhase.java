package cbs.dsl.api.context;

public enum TransactionPhase {
  /** Preview phase — validates inputs without mutating state. */
  PREVIEW,

  /** Execute phase — performs the business logic. */
  EXECUTE,

  /** Rollback phase — compensates a previously executed transaction. */
  ROLLBACK
}
