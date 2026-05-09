package cbs.dsl.api.context;

// TODO: remove
@Deprecated(forRemoval = true)
public enum TransactionPhase {
  /** Preview phase — validates inputs without mutating state. */
  PREVIEW,

  /** Execute phase — performs the business logic. */
  EXECUTE,

  /** Rollback phase — compensates a previously executed transaction. */
  ROLLBACK
}
