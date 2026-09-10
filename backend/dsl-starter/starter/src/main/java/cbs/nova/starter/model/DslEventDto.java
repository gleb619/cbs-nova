package cbs.nova.starter.model;

import cbs.nova.starter.entity.DslEventEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record DslEventDto(
        long id,
        String eventType,
        String aggregateType,
        String aggregateId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String correlationId,
        int schemaVersion,
        String createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode payload) {

  public static DslEventDto from(DslEventEntity entity, ObjectMapper objectMapper) {
    JsonNode payload;
    if (entity.payloadJson() == null) {
      payload = null;
    } else {
      try {
        payload = objectMapper.readTree(entity.payloadJson());
      } catch (Exception e) {
        throw new IllegalStateException(
                "Failed to parse stored payload for event id=" + entity.id(), e);
      }
    }
    return new DslEventDto(
            entity.id() != null ? entity.id() : 0L,
            entity.eventType(),
            entity.aggregateType(),
            entity.aggregateId(),
            entity.correlationId(),
            entity.schemaVersion(),
            entity.createdAt().toString(),
            payload);
  }
}
