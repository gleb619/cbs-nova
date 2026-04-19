package cbs.nova.model;

import java.time.OffsetDateTime;
import lombok.Builder;

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
