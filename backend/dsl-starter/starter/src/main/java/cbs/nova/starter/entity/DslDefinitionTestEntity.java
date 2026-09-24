package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("dsl_definition_tests")
public record DslDefinitionTestEntity(
        @Id @Column("id") @Nullable Long id,
        @Column("definition_name") String definitionName,
        @Column("case_name") String caseName,
        @Column("input") String inputJson,
        @Column("expected_output") String expectedOutputJson,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt) {

  public DslDefinitionTestEntity withUpdatedAt(Instant updatedAt) {
    return new DslDefinitionTestEntity(id, definitionName, caseName, inputJson,
            expectedOutputJson, createdAt, updatedAt);
  }
}
