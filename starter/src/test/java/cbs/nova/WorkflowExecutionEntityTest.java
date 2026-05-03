package cbs.nova;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class WorkflowExecutionEntityTest {

  @Test
  @DisplayName("Should create WorkflowExecutionEntity via builder with all fields")
  void shouldCreateWorkflowExecutionEntityViaBuilderWithAllFields() {
    var now = OffsetDateTime.now();
    var entity = WorkflowExecutionEntity.builder()
        .id(1L)
        .workflowCode("loan-approval")
        .dslVersion("1.0.0-abc1234")
        .currentState("INITIATED")
        .status(WorkflowStatus.ACTIVE)
        .context("{\"applicant\": \"John\"}")
        .displayData("{\"title\": \"Loan Application\"}")
        .performedBy("admin1")
        .createdAt(now)
        .updatedAt(now)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getWorkflowCode()).isEqualTo("loan-approval");
    assertThat(entity.getDslVersion()).isEqualTo("1.0.0-abc1234");
    assertThat(entity.getCurrentState()).isEqualTo("INITIATED");
    assertThat(entity.getStatus()).isEqualTo(WorkflowStatus.ACTIVE);
    assertThat(entity.getContext()).isEqualTo("{\"applicant\": \"John\"}");
    assertThat(entity.getDisplayData()).isEqualTo("{\"title\": \"Loan Application\"}");
    assertThat(entity.getPerformedBy()).isEqualTo("admin1");
    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getUpdatedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("Should create WorkflowExecutionEntity via no-args constructor")
  void shouldCreateWorkflowExecutionEntityViaNoArgsConstructor() {
    var entity = new WorkflowExecutionEntity();
    assertThat(entity).isNotNull();
  }
}
