package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslApiKeyEntity;
import java.time.Instant;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository for the {@code dsl_api_keys} table (T410). Writes flow through this
 * CRUD interface: inserts via {@code save} and the two conditional partial updates via
 * {@link #markRevoked} and {@link #touchLastUsed}.
 */
public interface ApiKeyCrudRepository extends CrudRepository<DslApiKeyEntity, Long> {

  @Modifying
  @Query("""
          UPDATE dsl_api_keys
          SET revoked_at = :revokedAt
          WHERE id = :id AND revoked_at IS NULL
          """)
  int markRevoked(long id, Instant revokedAt);

  @Modifying
  @Query("""
          UPDATE dsl_api_keys
          SET last_used_at = :lastUsedAt
          WHERE id = :id
          """)
  int touchLastUsed(long id, Instant lastUsedAt);
}
