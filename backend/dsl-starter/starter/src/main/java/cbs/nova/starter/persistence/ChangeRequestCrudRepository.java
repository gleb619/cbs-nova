package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.ChangeRequestEntity;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC repository for {@code dsl_change_request} (T568). Writes flow through this CRUD
 * interface: {@code save} for inserts, {@link #updateStatus} for unconditional status writes, and
 * {@link #transition} for compare-and-set status transitions.
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

  @Modifying
  @Query("""
          UPDATE dsl_change_request
          SET status = :toStatus, approved_by = :approvedBy, approved_at = :approvedAt,
              comment = :comment
          WHERE id = :id AND status = :fromStatus
          """)
  int transition(@Param("id") long id, @Param("fromStatus") String fromStatus,
          @Param("toStatus") String toStatus, @Nullable @Param("approvedBy") String approvedBy,
          @Nullable @Param("approvedAt") Instant approvedAt,
          @Nullable @Param("comment") String comment);
}
