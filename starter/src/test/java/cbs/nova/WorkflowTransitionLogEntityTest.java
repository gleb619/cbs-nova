package cbs.nova;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.entity.EventExecutionEntity;
import cbs.nova.entity.EventStatus;
import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowStatus;
import cbs.nova.entity.WorkflowTransitionLogEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class WorkflowTransitionLogEntityTest {

  @Test
  @DisplayName("Should create WorkflowTransitionLogEntity via builder with all fields")
  void shouldCreateWorkflowTransitionLogEntityViaBuilderWithAllFields() {
    var now = OffsetDateTime.now();
    var workflowExecution = WorkflowExecutionEntity.builder()
        .id(1L)
        .workflowCode("loan-approval")
        .dslVersion("1.0.0-abc1234")
        .currentState("INITIATED")
        .status(WorkflowStatus.ACTIVE)
        .context("{}")
        .displayData("{}")
        .performedBy("admin1")
        .createdAt(now)
        .updatedAt(now)
        .build();

    var eventExecution = EventExecutionEntity.builder()
        .id(1L)
        .eventCode("approve-loan")
        .dslVersion("1.0.0-abc1234")
        .action("execute")
        .status(EventStatus.COMPLETED)
        .context("{}")
        .executedTransactions("[]")
        .temporalWorkflowId("wf-123")
        .workflowExecution(workflowExecution)
        .performedBy("admin1")
        .createdAt(now)
        .updatedAt(now)
        .completedAt(now)
        .build();

    var entity = WorkflowTransitionLogEntity.builder()
        .id(1L)
        .workflowExecution(workflowExecution)
        .eventExecution(eventExecution)
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
    assertThat(entity.getWorkflowExecution()).isEqualTo(workflowExecution);
    assertThat(entity.getEventExecution()).isEqualTo(eventExecution);
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
  @DisplayName("Should create WorkflowTransitionLogEntity with null eventExecution and toState")
  void shouldCreateWorkflowTransitionLogEntityWithNullEventExecutionAndToState() {
    var now = OffsetDateTime.now();
    var workflowExecution = WorkflowExecutionEntity.builder()
        .id(1L)
        .workflowCode("loan-approval")
        .dslVersion("1.0.0-abc1234")
        .currentState("INITIATED")
        .status(WorkflowStatus.ACTIVE)
        .context("{}")
        .displayData("{}")
        .performedBy("admin1")
        .createdAt(now)
        .updatedAt(now)
        .build();

    var entity = WorkflowTransitionLogEntity.builder()
        .id(2L)
        .workflowExecution(workflowExecution)
        .eventExecution(null)
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

    assertThat(entity.getEventExecution()).isNull();
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
