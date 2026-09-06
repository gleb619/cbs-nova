package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslRunEntity;
import java.time.Instant;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JDBC repository for {@link DslRunEntity} reads.
 *
 * <p>
 * No {@code @Repository} annotation is used: Spring Data JDBC creates the implementation bean
 * automatically during repository scanning, which is already scoped to this package by
 * {@code @EnableJdbcRepositories(basePackages = "cbs.nova.starter.persistence")} in
 * {@code DslRunRepositoryConfiguration}.
 */
public interface DslRunJdbcRepository extends CrudRepository<DslRunEntity, Long> {

  Optional<DslRunEntity> findByRunId(String runId);

  List<DslRunEntity> findByProcessName(String processName);

  @Modifying
  @Query("""
          UPDATE dsl_runs
          SET status = :status,
              output_json = :outputJson,
              error_message = :errorMessage,
              context_json = :contextJson,
              finished_at = :finishedAt
          WHERE run_id = :runId AND status = 'RUNNING'
          """)
  int updateFinishedIfRunning(
          String runId,
          String status,
          String outputJson,
          String errorMessage,
          String contextJson,
          Instant finishedAt);

  @Modifying
  @Query("""
          UPDATE dsl_runs
          SET status = :status,
              output_json = :outputJson,
              error_message = :errorMessage,
              context_json = :contextJson,
              finished_at = :finishedAt
          WHERE run_id = :runId
          """)
  int updateFinishedByRunId(
          String runId,
          String status,
          String outputJson,
          String errorMessage,
          String contextJson,
          Instant finishedAt);

  @Modifying
  @Query("UPDATE dsl_runs SET status = :status WHERE run_id = :runId")
  int updateStatusByRunId(String runId, String status);
}
