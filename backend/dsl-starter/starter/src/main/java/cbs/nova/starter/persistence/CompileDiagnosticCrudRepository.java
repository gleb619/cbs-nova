package cbs.nova.starter.persistence;

import cbs.nova.starter.model.CompileDiagnosticRecord;
import java.time.Instant;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

/**
 * Spring Data JDBC access to the append-only {@code dsl_compile_diagnostics} table.
 *
 * <p>
 * Extends the plain {@link Repository} marker instead of {@code CrudRepository}: the diagnostic log
 * is append-only by convention, so only {@code save} is exposed.
 */
public interface CompileDiagnosticCrudRepository
        extends
          Repository<CompileDiagnosticRecord, Long> {

  CompileDiagnosticRecord save(CompileDiagnosticRecord entity);

  /**
   * Maintenance-only: deletes at most {@code batchSize} rows whose {@code occurred_at} is strictly
   * older than {@code cutoff}. Returns the number of rows actually deleted.
   *
   * <p>
   * Backed by the {@code idx_dsl_compile_diagnostics_occurred_at} index (V1__init.sql) so the
   * cutoff scan is bounded. The {@code LIMIT} clause sits inside a {@code WHERE IN (subquery)}
   * because PostgreSQL does not support {@code LIMIT} directly in {@code DELETE}; this shape is
   * portable across Postgres and the supported H2 modes. The caller is expected to loop on this
   * method until it returns less than {@code batchSize}.
   */
  @Modifying
  @Query("""
          DELETE FROM dsl_compile_diagnostics
          WHERE id IN (
              SELECT id FROM dsl_compile_diagnostics
              WHERE occurred_at < :cutoff
              LIMIT :batchSize
          )
          """)
  int deleteOlderThan(Instant cutoff, int batchSize);
}
