package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;


public record DslDefinitionTestEntity(
        @Nullable Long id,
        String definitionName,
        String caseName,
        String inputJson,
        String expectedOutputJson,
        Instant createdAt,
        Instant updatedAt) {

  public DslDefinitionTestEntity withUpdatedAt(Instant updatedAt) {
    return new DslDefinitionTestEntity(id, definitionName, caseName, inputJson,
            expectedOutputJson, createdAt, updatedAt);
  }
}
