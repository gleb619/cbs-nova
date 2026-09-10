package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Row of the {@code dsl_definition_tests} table: an author-defined example input plus expected
 * output attached to a published definition. {@code inputJson}/{@code expectedOutputJson} hold the
 * JSON payloads serialized as text (Postgres casts to {@code jsonb}; H2 stores as {@code TEXT}).
 */
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
