package cbs.nova.starter.persistence;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JDBC-backed {@link TransactionExecutionRepository} that persists successful transaction
 * executions and deserializes the {@code input} JSON field using Jackson.
 */
@RequiredArgsConstructor
public class JdbcTransactionExecutionRepository implements TransactionExecutionRepository {

  private static final String TABLE_NAME = "dsl_run_transactions";

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final TransactionExecutionJdbcRepository delegate;
  private final TransactionExecutionMapper mapper;
  private final ObjectMapper objectMapper;

  public JdbcTransactionExecutionRepository(
          DataSource dataSource,
          TransactionExecutionJdbcRepository delegate,
          TransactionExecutionMapper mapper,
          ObjectMapper objectMapper) {
    this(new NamedParameterJdbcTemplate(dataSource), delegate, mapper, objectMapper);
  }

  @Override
  public @NonNull TransactionExecution save(@NonNull TransactionExecution execution) {
    TransactionExecutionEntity entity = mapper.toEntity(execution);
    entity.setInputJson(serializeInput(execution.input()));

    KeyHolder keyHolder = new GeneratedKeyHolder();
    MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("runId", entity.getRunId())
            .addValue("transactionName", entity.getTransactionName())
            .addValue("inputJson", entity.getInputJson())
            .addValue("executedAt", Timestamp.from(entity.getExecutedAt()));

    jdbcTemplate.update(getInsertStatement(), params, keyHolder, new String[]{"id"});
    if (keyHolder.getKey() != null) {
      entity.setId(keyHolder.getKey().longValue());
    }
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
    String sql = "DELETE FROM " + TABLE_NAME + " WHERE run_id = :runId";
    jdbcTemplate.update(sql, new MapSqlParameterSource("runId", runId));
  }

  private TransactionExecution toDomain(TransactionExecutionEntity entity) {
    Object input = deserializeInput(entity.getInputJson());
    return new TransactionExecution(
            entity.getRunId(),
            entity.getTransactionName(),
            input,
            entity.getExecutedAt());
  }

  private @Nullable String serializeInput(@Nullable Object input) {
    if (input == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(input);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize transaction input", e);
    }
  }

  private @Nullable Object deserializeInput(@Nullable String inputJson) {
    if (inputJson == null) {
      return null;
    }
    try {
      return objectMapper.readValue(inputJson, Object.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize transaction input", e);
    }
  }

  private String getInsertStatement() {
    return """
            INSERT INTO %s (run_id, transaction_name, input_json, executed_at)
            VALUES
            (:runId, :transactionName, :inputJson, :executedAt)""".formatted(TABLE_NAME);
  }
}
