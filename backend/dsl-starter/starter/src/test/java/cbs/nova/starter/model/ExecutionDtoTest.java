package cbs.nova.starter.model;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

class ExecutionDtoTest {

  @Test
  void fromPopulatesTriggeredBy() {
    DslRun run = DslRun.builder()
            .runId("run-1")
            .processName("Loan")
            .status(DslRunStatus.COMPLETED.name())
            .input("{\\\"a\\\":1}")
            .output("{\\\"b\\\":2}")
            .error(null)
            .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .finishedAt(Instant.parse("2026-01-01T00:00:05Z"))
            .executionMode(ExecutionMode.RUN.name())
            .triggeredBy("alice")
            .build();

    ExecutionDto dto = ExecutionDto.from(run);

    assertThat(dto.triggeredBy()).isEqualTo("alice");
  }

  @Test
  void fromDetailCarriesTriggeredBy() {
    DslRun run = DslRun.builder()
            .runId("run-2")
            .processName("Loan")
            .status(DslRunStatus.COMPLETED.name())
            .input("{\\\"a\\\":1}")
            .output("{\\\"b\\\":2}")
            .error(null)
            .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .finishedAt(Instant.parse("2026-01-01T00:00:05Z"))
            .executionMode(ExecutionMode.RUN.name())
            .triggeredBy("bob")
            .build();

    ExecutionDto dto = ExecutionDto.fromDetail(run, new ObjectMapper());

    assertThat(dto.triggeredBy()).isEqualTo("bob");
  }

  @Test
  void fromOmitsTriggeredByWhenNull() {
    DslRun run = DslRun.builder()
            .runId("run-3")
            .processName("Loan")
            .status(DslRunStatus.RUNNING.name())
            .input(null)
            .output(null)
            .error(null)
            .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .triggeredBy(null)
            .build();

    ExecutionDto dto = ExecutionDto.from(run);

    assertThat(dto.triggeredBy()).isNull();
  }
}
