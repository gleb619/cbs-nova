package cbs.nova.starter.converter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.starter.entity.DslRunEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class DslRunMapperTest {

  private final DslRunMapper mapper = Mappers.getMapper(DslRunMapper.class);

  private static final Instant STARTED_AT = Instant.parse("2026-09-01T10:00:00Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-09-01T10:00:05Z");

  @Test
  void mapsDomainToEntityRenamingJsonFields() {
    DslRun run = DslRun.builder()
            .runId("run-1")
            .processName("OrderFlow")
            .status("COMPLETED")
            .input("{\"order\":1}")
            .output("{\"ok\":true}")
            .error(null)
            .contextJson("{\"tenant\":\"t1\"}")
            .startedAt(STARTED_AT)
            .finishedAt(FINISHED_AT)
            .executionMode("PREVIEW")
            .triggeredBy("api")
            .correlationId("corr-1")
            .definitionHash("hash-1")
            .build();

    DslRunEntity entity = mapper.toEntity(run);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getRunId()).isEqualTo("run-1");
    assertThat(entity.getProcessName()).isEqualTo("OrderFlow");
    assertThat(entity.getStatus()).isEqualTo("COMPLETED");
    assertThat(entity.getInputJson()).isEqualTo("{\"order\":1}");
    assertThat(entity.getOutputJson()).isEqualTo("{\"ok\":true}");
    assertThat(entity.getErrorMessage()).isNull();
    assertThat(entity.getContextJson()).isEqualTo("{\"tenant\":\"t1\"}");
    assertThat(entity.getStartedAt()).isEqualTo(STARTED_AT);
    assertThat(entity.getFinishedAt()).isEqualTo(FINISHED_AT);
    assertThat(entity.getExecutionMode()).isEqualTo("PREVIEW");
    assertThat(entity.getTriggeredBy()).isEqualTo("api");
    assertThat(entity.getCorrelationId()).isEqualTo("corr-1");
    assertThat(entity.getDefinitionHash()).isEqualTo("hash-1");
  }

  @Test
  void mapsEntityToDomainRenamingJsonFieldsBack() {
    DslRunEntity entity = new DslRunEntity();
    entity.setId(7L);
    entity.setRunId("run-2");
    entity.setProcessName("Ping");
    entity.setStatus("FAILED");
    entity.setInputJson("{\"in\":1}");
    entity.setOutputJson("{\"out\":2}");
    entity.setErrorMessage("kaboom");
    entity.setContextJson("{\"ctx\":3}");
    entity.setStartedAt(STARTED_AT);
    entity.setFinishedAt(FINISHED_AT);
    entity.setExecutionMode("EXECUTE");
    entity.setTriggeredBy("scheduler");
    entity.setCorrelationId("corr-2");
    entity.setDefinitionHash("hash-2");

    DslRun run = mapper.toDomain(entity);

    assertThat(run.runId()).isEqualTo("run-2");
    assertThat(run.processName()).isEqualTo("Ping");
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.input()).isEqualTo("{\"in\":1}");
    assertThat(run.output()).isEqualTo("{\"out\":2}");
    assertThat(run.error()).isEqualTo("kaboom");
    assertThat(run.contextJson()).isEqualTo("{\"ctx\":3}");
    assertThat(run.startedAt()).isEqualTo(STARTED_AT);
    assertThat(run.finishedAt()).isEqualTo(FINISHED_AT);
    assertThat(run.executionMode()).isEqualTo("EXECUTE");
    assertThat(run.triggeredBy()).isEqualTo("scheduler");
    assertThat(run.correlationId()).isEqualTo("corr-2");
    assertThat(run.definitionHash()).isEqualTo("hash-2");
  }

  @Test
  void entityIdIsNotMappedToDomain() {
    DslRunEntity entity = new DslRunEntity();
    entity.setId(7L);
    entity.setRunId("run-3");
    entity.setProcessName("Ping");
    entity.setStatus("RUNNING");
    entity.setStartedAt(STARTED_AT);

    DslRun run = mapper.toDomain(entity);

    // Domain record has no id field — entity id is dropped silently.
    assertThat(run.runId()).isEqualTo("run-3");
  }

  @Test
  void nullableFieldsRoundTripAsNulls() {
    DslRun run = DslRun.builder()
            .runId("run-4")
            .processName("Ping")
            .status("RUNNING")
            .startedAt(STARTED_AT)
            .build();

    DslRunEntity entity = mapper.toEntity(run);

    assertThat(entity.getInputJson()).isNull();
    assertThat(entity.getOutputJson()).isNull();
    assertThat(entity.getErrorMessage()).isNull();
    assertThat(entity.getContextJson()).isNull();
    assertThat(entity.getFinishedAt()).isNull();
    assertThat(entity.getExecutionMode()).isNull();
    assertThat(entity.getTriggeredBy()).isNull();
    assertThat(entity.getCorrelationId()).isNull();
    assertThat(entity.getDefinitionHash()).isNull();

    DslRun roundTrip = mapper.toDomain(entity);
    assertThat(roundTrip.input()).isNull();
    assertThat(roundTrip.output()).isNull();
    assertThat(roundTrip.error()).isNull();
    assertThat(roundTrip.finishedAt()).isNull();
  }
}
