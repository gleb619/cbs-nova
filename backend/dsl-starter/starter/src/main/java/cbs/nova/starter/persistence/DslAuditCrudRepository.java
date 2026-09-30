package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslAuditEntity;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

/**
 * Spring Data JDBC access to the append-only {@code dsl_audit} table.
 *
 * <p>
 * Deliberately extends the plain {@link Repository} marker instead of {@code CrudRepository}: the
 * latter would drag in {@code delete}/{@code count} methods that violate the append-only invariant
 * — the audit log is insert + read only. Exposing exactly {@code save} plus the paged finders keeps
 * that guarantee at compile level; no mutation method is reachable.
 *
 * <p>
 * Both finders order by {@code occurred_at DESC, id DESC}, matching the {@code ORDER BY} clause of
 * the hand-rolled JDBC repository this interface replaces.
 *
 * <p>
 * <b>Maintenance-only delete:</b> {@link #deleteOlderThan(Instant, int)} is the single exception to
 * the append-only invariant and exists solely so the opt-in {@code dsl.maintenance.audit-retention}
 * purge (T747) can age rows out of the table. It is intentionally narrow — fixed SQL, no cascading
 * effects — and is not exposed from any REST handler. See
 * {@link DslAuditStore#purgeOlderThan(Instant, int)}.
 */
public interface DslAuditCrudRepository extends Repository<DslAuditEntity, Long> {

  DslAuditEntity save(DslAuditEntity entity);

  Page<DslAuditEntity> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

  Page<DslAuditEntity> findByActionOrderByOccurredAtDescIdDesc(String action, Pageable pageable);

  /**
   * Maintenance-only: deletes at most {@code batchSize} rows whose {@code occurred_at} is strictly
   * older than {@code cutoff}. Returns the number of rows actually deleted.
   *
   * <p>
   * Backed by the {@code idx_dsl_audit_occurred_at} index (V1__init.sql) so the cutoff scan is
   * bounded. The {@code LIMIT} clause sits inside a {@code WHERE IN (subquery)} because PostgreSQL
   * does not support {@code LIMIT} directly in {@code DELETE}; this shape is portable across
   * Postgres and the supported H2 modes. The caller is expected to loop on this method until it
   * returns less than {@code batchSize}.
   */
  @Modifying
  @Query("""
          DELETE FROM dsl_audit
          WHERE id IN (
              SELECT id FROM dsl_audit
              WHERE occurred_at < :cutoff
              LIMIT :batchSize
          )
          """)
  int deleteOlderThan(Instant cutoff, int batchSize);
}
