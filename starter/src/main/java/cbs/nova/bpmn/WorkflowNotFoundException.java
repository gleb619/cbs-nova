package cbs.nova.bpmn;

/**
 * Thrown when a requested workflow code is not found in the DSL registry.
 */
public class WorkflowNotFoundException extends RuntimeException {

  public WorkflowNotFoundException(String workflowCode) {
    super("Workflow not found: code=" + workflowCode);
  }
}
