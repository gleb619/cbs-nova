package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.ChangeRequestEntity;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository for {@code dsl_change_request} (T568). Writes flow through this CRUD
 * interface: {@code save} for inserts and {@link #updateStatus} for status transitions.
 */
public interface ChangeRequestCrudRepository
        extends
          CrudRepository<ChangeRequestEntity, Long> {

  @Modifying
  @Query("""
          UPDATE dsl_change_request
          SET status = :status, approved_by = :approvedBy, approved_at = :approvedAt,
              comment = :comment
          WHERE id = :id
          """)
  int updateStatus(long id, String status, @Nullable String approvedBy,
          @Nullable Instant approvedAt, @Nullable String comment);
}
