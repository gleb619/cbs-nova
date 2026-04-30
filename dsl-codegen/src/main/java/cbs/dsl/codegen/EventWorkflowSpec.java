package cbs.dsl.codegen;

import java.util.List;

/**
 * Specification for generating a Temporal workflow class for an event.
 *
 * @param eventCode the canonical event code (e.g., "LOAN_SUBMIT")
 * @param eventClassName the fully-qualified class name of the event definition
 * @param transactionCodes ordered list of transaction codes to execute
 */
public record EventWorkflowSpec(
    String eventCode, String eventClassName, List<String> transactionCodes) {}
