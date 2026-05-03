package cbs.nova.model;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class MassOperationItemDto {

  Long id;
  String itemKey;
  String status;
  String errorMessage;
  OffsetDateTime startedAt;
  OffsetDateTime completedAt;
}
