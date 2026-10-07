package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslDefinitionSnapshotEntity;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository for {@code dsl_definition_snapshots} (T875). Explicit SQL keeps the
 * queries dialect-agnostic across the Postgres and H2 migration twins.
 */
public interface DslDefinitionSnapshotCrudRepository
        extends
          CrudRepository<DslDefinitionSnapshotEntity, Long> {

  @Query("SELECT * FROM dsl_definition_snapshots WHERE definition_name = :definitionName"
          + " ORDER BY captured_at DESC, id DESC")
  List<DslDefinitionSnapshotEntity> findByDefinitionName(String definitionName);

  @Query("SELECT * FROM dsl_definition_snapshots WHERE definition_name = :definitionName"
          + " AND content_hash = :contentHash")
  List<DslDefinitionSnapshotEntity> findByDefinitionNameAndContentHash(
          String definitionName, String contentHash);

  @Query("SELECT COUNT(*) FROM dsl_definition_snapshots WHERE definition_name = :definitionName"
          + " AND content_hash = :contentHash")
  long countByDefinitionNameAndContentHash(String definitionName, String contentHash);
}
