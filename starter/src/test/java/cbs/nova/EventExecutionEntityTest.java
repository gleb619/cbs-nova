package cbs.nova;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.entity.EventExecutionEntity;
import cbs.nova.entity.EventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class EventExecutionEntityTest {

  @Test
  @DisplayName("Should create EventExecutionEntity via builder with all fields")
  void shouldCreateEventExecutionEntityViaBuilderWithAllFields() {
    var now = OffsetDateTime.now();

    var entity = EventExecutionEntity.builder()
        .id(1L)
        .eventCode("approve-loan")
        .dslVersion("1.0.0-abc1234")
        .action("execute")
        .status(EventStatus.COMPLETED)
        .context("{\"amount\": 5000}")
        .executedTransactions("[{\"type\": \"debit\"}]")
        .temporalWorkflowId("wf-123")
        .workflowExecutionId(1L)
        .performedBy("admin1")
        .createdAt(now)
        .updatedAt(now)
        .completedAt(now)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getEventCode()).isEqualTo("approve-loan");
    assertThat(entity.getDslVersion()).isEqualTo("1.0.0-abc1234");
    assertThat(entity.getAction()).isEqualTo("execute");
    assertThat(entity.getStatus()).isEqualTo(EventStatus.COMPLETED);
    assertThat(entity.getContext()).isEqualTo("{\"amount\": 5000}");
    assertThat(entity.getExecutedTransactions()).isEqualTo("[{\"type\": \"debit\"}]");
    assertThat(entity.getTemporalWorkflowId()).isEqualTo("wf-123");
    assertThat(entity.getWorkflowExecutionId()).isEqualTo(1L);
    assertThat(entity.getPerformedBy()).isEqualTo("admin1");
    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getUpdatedAt()).isEqualTo(now);
    assertThat(entity.getCompletedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("Should create EventExecutionEntity with null temporalWorkflowId and completedAt")
  void shouldCreateEventExecutionEntityWithNullTemporalWorkflowIdAndCompletedAt() {
    var now = OffsetDateTime.now();

    var entity = EventExecutionEntity.builder()
        .id(2L)
        .eventCode("review-loan")
        .dslVersion("1.0.0-abc1234")
        .action("execute")
        .status(EventStatus.RUNNING)
        .context("{}")
        .executedTransactions("[]")
        .temporalWorkflowId(null)
        .workflowExecutionId(1L)
        .performedBy("user1")
        .createdAt(now)
        .updatedAt(now)
        .completedAt(null)
        .build();

    assertThat(entity.getTemporalWorkflowId()).isNull();
    assertThat(entity.getCompletedAt()).isNull();
    assertThat(entity.getStatus()).isEqualTo(EventStatus.RUNNING);
  }

  @Test
  @DisplayName("Should create EventExecutionEntity via no-args constructor")
  void shouldCreateEventExecutionEntityViaNoArgsConstructor() {
    var entity = new EventExecutionEntity();
    assertThat(entity).isNotNull();
  }
}
