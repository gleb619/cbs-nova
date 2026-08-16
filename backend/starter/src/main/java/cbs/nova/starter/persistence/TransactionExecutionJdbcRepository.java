package cbs.nova.starter.persistence;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Spring Data JDBC repository for {@link TransactionExecutionEntity} reads.
 *
 * <p>
 * No {@code @Repository} annotation is used: Spring Data JDBC creates the implementation bean
 * automatically during repository scanning.
 */
public interface TransactionExecutionJdbcRepository
        extends
          CrudRepository<TransactionExecutionEntity, Long> {

  List<TransactionExecutionEntity> findByRunId(String runId);
}
