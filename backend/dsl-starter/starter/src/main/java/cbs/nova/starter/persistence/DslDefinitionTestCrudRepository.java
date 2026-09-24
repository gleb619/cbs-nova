package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslDefinitionTestEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository for the {@code dsl_definition_tests} sidecar table (T409). Writes
 * flow through this CRUD interface: {@code save} for inserts and {@link #deleteByDefinitionName} to
 * clear a definition's whole case set.
 */
public interface DslDefinitionTestCrudRepository
        extends
          CrudRepository<DslDefinitionTestEntity, Long> {

  @Modifying
  @Query("DELETE FROM dsl_definition_tests WHERE definition_name = :definitionName")
  int deleteByDefinitionName(String definitionName);
}
