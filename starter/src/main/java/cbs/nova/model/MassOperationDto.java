package cbs.nova.model;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class MassOperationDto {

  Long id;
  String code;
  String category;
  String dslVersion;
  String status;
  Long totalItems;
  Long processedCount;
  Long failedCount;
  String triggerType;
  String triggerSource;
  String performedBy;
  OffsetDateTime startedAt;
  OffsetDateTime completedAt;
  String temporalWorkflowId;
}
