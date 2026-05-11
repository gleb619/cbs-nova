package cbs.dsl.codegen;

import java.util.List;

/**
 * Specification for generating a Temporal workflow class for an event.
 *
 * @param eventCode the canonical event code (e.g., "LOAN_SUBMIT")
 * @param eventClassName the fully-qualified class name of the event definition (for code events)
 * @param transactionCodes ordered list of transaction codes to execute
 * @param dslBody the embedded DSL body for the event
 * @param dslImports the imports required by the embedded DSL body
 * @param workflowImplClassName optional fully-qualified workflow impl class; if null, defaults to
 *     {@code definitions.{SimpleName}Definition}
 */
public record EventSpecificationModel(
    String eventCode,
    String eventClassName,
    List<String> transactionCodes,
    String dslBody,
    String dslImports,
    String workflowImplClassName) {

  public EventSpecificationModel(
      String eventCode, String eventClassName, List<String> transactionCodes) {
    this(eventCode, eventClassName, transactionCodes, null, null, null);
  }

  public EventSpecificationModel(
      String eventCode, String eventClassName, List<String> transactionCodes, String dslBody, String dslImports) {
    this(eventCode, eventClassName, transactionCodes, dslBody, dslImports, null);
  }
}
