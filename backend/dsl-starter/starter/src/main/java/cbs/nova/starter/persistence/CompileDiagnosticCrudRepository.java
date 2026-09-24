package cbs.nova.starter.persistence;

import cbs.nova.starter.model.CompileDiagnosticRecord;
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
}
