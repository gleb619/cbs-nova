package cbs.nova;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class MassOperationExecutionEntityTest {

  @Test
  @DisplayName("Should create MassOperationExecutionEntity via builder with all fields")
  void shouldCreateMassOperationExecutionEntityViaBuilderWithAllFields() {
    var now = OffsetDateTime.now();
    var entity = MassOperationExecutionEntity.builder()
        .id(1L)
        .code("daily-interest")
        .category("loan")
        .dslVersion("1.0.0-abc1234")
        .status(MassOperationStatus.RUNNING)
        .context("{\"rate\": 0.05}")
        .totalItems(100L)
        .processedCount(0L)
        .failedCount(0L)
        .triggerType("SCHEDULED")
        .triggerSource("cron-job")
        .performedBy("system")
        .startedAt(now)
        .completedAt(null)
        .temporalWorkflowId("wf-123")
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getCode()).isEqualTo("daily-interest");
    assertThat(entity.getCategory()).isEqualTo("loan");
    assertThat(entity.getDslVersion()).isEqualTo("1.0.0-abc1234");
    assertThat(entity.getStatus()).isEqualTo(MassOperationStatus.RUNNING);
    assertThat(entity.getContext()).isEqualTo("{\"rate\": 0.05}");
    assertThat(entity.getTotalItems()).isEqualTo(100L);
    assertThat(entity.getProcessedCount()).isEqualTo(0L);
    assertThat(entity.getFailedCount()).isEqualTo(0L);
    assertThat(entity.getTriggerType()).isEqualTo("SCHEDULED");
    assertThat(entity.getTriggerSource()).isEqualTo("cron-job");
    assertThat(entity.getPerformedBy()).isEqualTo("system");
    assertThat(entity.getStartedAt()).isEqualTo(now);
    assertThat(entity.getCompletedAt()).isNull();
    assertThat(entity.getTemporalWorkflowId()).isEqualTo("wf-123");
  }

  @Test
  @DisplayName("Should create MassOperationExecutionEntity via no-args constructor")
  void shouldCreateMassOperationExecutionEntityViaNoArgsConstructor() {
    var entity = new MassOperationExecutionEntity();
    assertThat(entity).isNotNull();
  }
}
