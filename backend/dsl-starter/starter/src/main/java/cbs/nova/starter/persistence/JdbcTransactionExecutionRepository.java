package cbs.nova.starter.persistence;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.starter.converter.TransactionExecutionMapper;
import cbs.nova.starter.entity.TransactionExecutionEntity;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JdbcTransactionExecutionRepository implements TransactionExecutionRepository {

  private final TransactionExecutionJdbcRepository delegate;
  private final TransactionExecutionMapper mapper;
  private final ObjectMapper objectMapper;

  @Override
  public @NonNull TransactionExecution save(@NonNull TransactionExecution execution) {
    TransactionExecutionEntity entity = mapper.toEntity(execution);
    entity.setInputJson(serializeInput(execution.input()));

    delegate.findTopByRunIdAndTransactionNameOrderByIdDesc(
            execution.runId(), execution.transactionName())
            .ifPresent(existing -> entity.setId(existing.getId()));
    delegate.save(entity);

    return execution;
  }

  @Override
  public @NonNull List<TransactionExecution> findByRunId(@NonNull String runId) {
    return delegate.findByRunIdOrderByIdDesc(runId).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
  }

  @Override
  public void deleteByRunId(@NonNull String runId) {
    delegate.deleteByRunId(runId);
  }

  @Override
  public int deleteByRunIds(@NonNull Collection<String> runIds) {
    if (runIds.isEmpty()) {
      return 0;
    }
    return delegate.deleteByRunIds(runIds);
  }

  private TransactionExecution toDomain(TransactionExecutionEntity entity) {
    Object input = deserializeInput(entity.getInputJson());
    return new TransactionExecution(
            entity.getRunId(),
            entity.getTransactionName(),
            input,
            entity.getExecutedAt(),
            entity.getStartedAt(),
            entity.getFinishedAt(),
            mapper.mapStatus(entity.getStatus()),
            entity.getErrorMessage());
  }

  private @Nullable String serializeInput(@Nullable Object input) {
    if (input == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(input);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize transaction input", e);
    }
  }

  private @Nullable Object deserializeInput(@Nullable String inputJson) {
    if (inputJson == null) {
      return null;
    }
    try {
      return objectMapper.readValue(inputJson, Object.class);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to deserialize transaction input", e);
    }
  }
}
