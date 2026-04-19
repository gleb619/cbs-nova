package cbs.nova.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record EventWorkflowRequest(
    String workflowCode,
    String eventCode,
    String contextJson,
    String performedBy,
    String dslVersion) {}
