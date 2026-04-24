package cbs.dsl.api;

/** Workflow action types used to drive state transitions. */
public enum Action {
  /** Preview-only action, does not change workflow state. */
  PREVIEW,
  /** Submit the workflow for processing. */
  SUBMIT,
  /** Approve the current step. */
  APPROVE,
  /** Reject the current step. */
  REJECT,
  /** Cancel the workflow. */
  CANCEL,
  /** Close the workflow as completed. */
  CLOSE,
  /** Rollback the last transaction. */
  ROLLBACK,
}
