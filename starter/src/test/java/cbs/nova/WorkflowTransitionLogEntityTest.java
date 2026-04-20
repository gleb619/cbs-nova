package cbs.nova;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.entity.WorkflowTransitionLogEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class WorkflowTransitionLogEntityTest {

  @Test
  @DisplayName("Should create WorkflowTransitionLogEntity via builder with all fields")
  void shouldCreateWorkflowTransitionLogEntityViaBuilderWithAllFields() {
    var now = OffsetDateTime.now();

    var entity = WorkflowTransitionLogEntity.builder()
        .id(1L)
        .workflowExecutionId(1L)
        .eventExecutionId(1L)
        .action("approve")
        .fromState("INITIATED")
        .toState("APPROVED")
        .status("COMPLETED")
        .faultMessage(null)
        .dslVersion("1.0.0-abc1234")
        .performedBy("admin1")
        .createdAt(now)
        .completedAt(now)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getWorkflowExecutionId()).isEqualTo(1L);
    assertThat(entity.getEventExecutionId()).isEqualTo(1L);
    assertThat(entity.getAction()).isEqualTo("approve");
    assertThat(entity.getFromState()).isEqualTo("INITIATED");
    assertThat(entity.getToState()).isEqualTo("APPROVED");
    assertThat(entity.getStatus()).isEqualTo("COMPLETED");
    assertThat(entity.getFaultMessage()).isNull();
    assertThat(entity.getDslVersion()).isEqualTo("1.0.0-abc1234");
    assertThat(entity.getPerformedBy()).isEqualTo("admin1");
    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getCompletedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("Should create WorkflowTransitionLogEntity with null eventExecutionId and toState")
  void shouldCreateWorkflowTransitionLogEntityWithNullEventExecutionAndToState() {
    var now = OffsetDateTime.now();

    var entity = WorkflowTransitionLogEntity.builder()
        .id(2L)
        .workflowExecutionId(1L)
        .eventExecutionId(null)
        .action("start")
        .fromState("INIT")
        .toState(null)
        .status("FAULTED")
        .faultMessage("Something went wrong")
        .dslVersion("1.0.0-abc1234")
        .performedBy("system")
        .createdAt(now)
        .completedAt(null)
        .build();

    assertThat(entity.getEventExecutionId()).isNull();
    assertThat(entity.getToState()).isNull();
    assertThat(entity.getFaultMessage()).isEqualTo("Something went wrong");
    assertThat(entity.getCompletedAt()).isNull();
  }

  @Test
  @DisplayName("Should create WorkflowTransitionLogEntity via no-args constructor")
  void shouldCreateWorkflowTransitionLogEntityViaNoArgsConstructor() {
    var entity = new WorkflowTransitionLogEntity();
    assertThat(entity).isNotNull();
  }
}
