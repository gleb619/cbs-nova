package cbs.dsl.codegen;

import java.util.List;

/**
 * Specification for generating a Temporal workflow class for an event.
 *
 * @param eventCode the canonical event code (e.g., "LOAN_SUBMIT")
 * @param eventClassName the fully-qualified class name of the event definition (for code events)
 * @param transactionCodes ordered list of transaction codes to execute
 * @param workflowImplClassName optional fully-qualified workflow impl class; if null, defaults to
 *     {@code definitions.{SimpleName}Definition}
 */
public record EventWorkflowModel(
    String eventCode,
    String eventClassName,
    List<String> transactionCodes,
    String workflowImplClassName) {

  public EventWorkflowModel(String eventCode, String eventClassName, List<String> transactionCodes) {
    this(eventCode, eventClassName, transactionCodes, null);
  }
}
