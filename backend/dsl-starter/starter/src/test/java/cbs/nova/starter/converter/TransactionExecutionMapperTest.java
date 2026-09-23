package cbs.nova.starter.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.dsl.transaction.TransactionExecutionStatus;
import cbs.nova.starter.entity.TransactionExecutionEntity;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class TransactionExecutionMapperTest {

  private final TransactionExecutionMapper mapper = Mappers
          .getMapper(TransactionExecutionMapper.class);

  private static final Instant EXECUTED_AT = Instant.parse("2026-09-01T10:00:00Z");
  private static final Instant STARTED_AT = Instant.parse("2026-09-01T10:00:01Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-09-01T10:00:03Z");

  @Test
  void mapsDomainToEntityConvertingStatusToString() {
    TransactionExecution execution = new TransactionExecution(
            "run-1", "ChargeCard", Map.of("k", "v"), EXECUTED_AT, STARTED_AT, FINISHED_AT,
            TransactionExecutionStatus.COMPENSATED, "boom");

    TransactionExecutionEntity entity = mapper.toEntity(execution);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getRunId()).isEqualTo("run-1");
    assertThat(entity.getTransactionName()).isEqualTo("ChargeCard");
    assertThat(entity.getStatus()).isEqualTo("COMPENSATED");
    assertThat(entity.getStartedAt()).isEqualTo(STARTED_AT);
    assertThat(entity.getFinishedAt()).isEqualTo(FINISHED_AT);
    assertThat(entity.getExecutedAt()).isEqualTo(EXECUTED_AT);
    assertThat(entity.getErrorMessage()).isEqualTo("boom");
  }

  @Test
  void mapsEntityToDomainConvertingStringToStatus() {
    TransactionExecutionEntity entity = new TransactionExecutionEntity();
    entity.setId(42L);
    entity.setRunId("run-2");
    entity.setTransactionName("ReserveStock");
    entity.setInputJson("{\"sku\":1}");
    entity.setStatus("FAILED");
    entity.setStartedAt(STARTED_AT);
    entity.setFinishedAt(FINISHED_AT);
    entity.setErrorMessage("insufficient");
    entity.setExecutedAt(EXECUTED_AT);

    TransactionExecution execution = mapper.toDomain(entity);

    assertThat(execution.runId()).isEqualTo("run-2");
    assertThat(execution.transactionName()).isEqualTo("ReserveStock");
    assertThat(execution.status()).isEqualTo(TransactionExecutionStatus.FAILED);
    assertThat(execution.startedAt()).isEqualTo(STARTED_AT);
    assertThat(execution.finishedAt()).isEqualTo(FINISHED_AT);
    assertThat(execution.executedAt()).isEqualTo(EXECUTED_AT);
    assertThat(execution.error()).isEqualTo("insufficient");
  }

  @Test
  void entityIdIsNotMappedBackToDomain() {
    TransactionExecutionEntity entity = new TransactionExecutionEntity();
    entity.setId(42L);
    entity.setRunId("run-3");
    entity.setTransactionName("Tx");
    entity.setStatus("SUCCESS");
    entity.setStartedAt(STARTED_AT);
    entity.setExecutedAt(EXECUTED_AT);

    TransactionExecution execution = mapper.toDomain(entity);

    // Domain record has no id field — entity id is dropped silently.
    assertThat(execution.runId()).isEqualTo("run-3");
  }

  @Test
  void nullStatusMapsToNullInBothDirections() {
    TransactionExecutionEntity entity = new TransactionExecutionEntity();
    entity.setRunId("run-4");
    entity.setTransactionName("Tx");
    entity.setStatus(null);
    entity.setStartedAt(STARTED_AT);
    entity.setExecutedAt(EXECUTED_AT);

    TransactionExecution execution = mapper.toDomain(entity);
    assertThat(execution.status()).isNull();

    TransactionExecutionEntity roundTrip = mapper.toEntity(execution);
    assertThat(roundTrip.getStatus()).isNull();
  }

  @Test
  void toEntityIgnoresInputJsonQuirk() {
    // Quirk: inputJson is repository-serialized, so toEntity never populates it,
    // even when the domain input is present.
    TransactionExecution execution = new TransactionExecution(
            "run-5", "Tx", Map.of("k", "v"), EXECUTED_AT, STARTED_AT, FINISHED_AT,
            TransactionExecutionStatus.SUCCESS, null);

    TransactionExecutionEntity entity = mapper.toEntity(execution);

    assertThat(entity.getInputJson()).isNull();
    assertThat(entity.getErrorMessage()).isNull();
  }

  @Test
  void unknownStatusStringThrowsOnDomainMapping() {
    TransactionExecutionEntity entity = new TransactionExecutionEntity();
    entity.setRunId("run-6");
    entity.setTransactionName("Tx");
    entity.setStatus("BOGUS");
    entity.setStartedAt(STARTED_AT);
    entity.setExecutedAt(EXECUTED_AT);

    assertThatThrownBy(() -> mapper.toDomain(entity))
            .isInstanceOf(IllegalArgumentException.class);
  }

}
