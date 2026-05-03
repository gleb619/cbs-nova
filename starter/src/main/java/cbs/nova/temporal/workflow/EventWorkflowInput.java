package cbs.nova.temporal.workflow;

public record EventWorkflowInput(
    String workflowCode,
    String eventCode,
    String contextJson,
    String performedBy,
    String dslVersion) {

}
