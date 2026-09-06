package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.TransactionExecutionEntity;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

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

  @NonNull
  List<TransactionExecutionEntity> findByRunIdOrderByIdDesc(@NonNull String runId);

  @NonNull
  Optional<TransactionExecutionEntity> findTopByRunIdAndTransactionNameOrderByIdDesc(
          @NonNull String runId,
          @NonNull String transactionName);

  @Modifying
  @Query("DELETE FROM dsl_run_transactions WHERE run_id = :runId")
  void deleteByRunId(@NonNull String runId);

  @Modifying
  @Query("DELETE FROM dsl_run_transactions WHERE run_id IN (:runIds)")
  int deleteByRunIds(@NonNull Collection<String> runIds);

  @Modifying
  @Query("""
          UPDATE dsl_run_transactions
          SET status = :status,
              error_message = :errorMessage
          WHERE run_id = :runId AND transaction_name = :transactionName
          """)
  int updateStatusAndErrorByRunIdAndTransactionName(
          @NonNull String runId,
          @NonNull String transactionName,
          @NonNull String status,
          @Nullable String errorMessage);
}
