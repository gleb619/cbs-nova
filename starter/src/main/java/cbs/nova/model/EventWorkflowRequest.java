package cbs.nova.model;

import lombok.Builder;

import java.util.List;

// TODO: remove file, instead use another api
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record EventWorkflowRequest(
    String workflowCode,
    String eventCode,
    String contextJson,
    String performedBy,
    String dslVersion,
    List<String> transactionCodes) {}
