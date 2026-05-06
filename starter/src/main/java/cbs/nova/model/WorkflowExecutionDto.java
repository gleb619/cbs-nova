package cbs.nova.model;

import lombok.Builder;

import java.time.OffsetDateTime;

//TODO: remove file, instead use another api
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record WorkflowExecutionDto(
    Long id,
    String workflowCode,
    String dslVersion,
    String currentState,
    String status,
    String context,
    String displayData,
    String performedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
