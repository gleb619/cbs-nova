package cbs.nova.model;

import lombok.Builder;

import java.time.OffsetDateTime;

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
