package cbs.nova.starter.repository;

import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Repository
@RequiredArgsConstructor
@ConditionalOnBean(DataSource.class)
public class JdbcDslRunRepository implements DslRunRepository {

  private final DslRunJdbcRepository delegate;

  @Override
  public @NonNull DslRun save(@NonNull DslRun run) {
    DslRunEntity existing = delegate.findByRunId(run.runId()).orElse(null);
    DslRunEntity entity = toEntity(run, existing);
    DslRunEntity saved = delegate.save(entity);
    return toDomain(saved);
  }

  @Override
  public @NonNull Optional<DslRun> findByRunId(@NonNull String runId) {
    return delegate.findByRunId(runId).map(this::toDomain);
  }

  @Override
  public @NonNull List<DslRun> findByProcessName(@NonNull String processName) {
    return delegate.findByProcessName(processName).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
  }

  private DslRunEntity toEntity(DslRun run, DslRunEntity existing) {
    DslRunEntity entity = existing != null ? existing : new DslRunEntity();
    entity.setRunId(run.runId());
    entity.setProcessName(run.processName());
    entity.setStatus(run.status());
    entity.setInputJson(run.input());
    entity.setOutputJson(run.output());
    entity.setErrorMessage(run.error());
    entity.setStartedAt(run.startedAt());
    entity.setFinishedAt(run.finishedAt());
    entity.setExecutionMode(run.executionMode());
    return entity;
  }

  private DslRun toDomain(DslRunEntity entity) {
    return DslRun.builder()
            .runId(entity.getRunId())
            .processName(entity.getProcessName())
            .status(entity.getStatus())
            .input(entity.getInputJson())
            .output(entity.getOutputJson())
            .error(entity.getErrorMessage())
            .startedAt(entity.getStartedAt())
            .finishedAt(entity.getFinishedAt())
            .executionMode(entity.getExecutionMode())
            .build();
  }
}
