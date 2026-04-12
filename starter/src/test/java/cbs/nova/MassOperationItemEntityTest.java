package cbs.nova;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemEntity;
import cbs.nova.entity.MassOperationItemStatus;
import cbs.nova.entity.MassOperationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class MassOperationItemEntityTest {

  @Test
  @DisplayName("Should create MassOperationItemEntity via builder with all fields")
  void shouldCreateMassOperationItemEntityViaBuilderWithAllFields() {
    var now = OffsetDateTime.now();
    var execution = MassOperationExecutionEntity.builder()
        .id(1L)
        .code("daily-interest")
        .category("loan")
        .dslVersion("1.0.0-abc1234")
        .status(MassOperationStatus.RUNNING)
        .context("{}")
        .totalItems(100L)
        .processedCount(0L)
        .failedCount(0L)
        .triggerType("SCHEDULED")
        .performedBy("system")
        .startedAt(now)
        .build();

    var entity = MassOperationItemEntity.builder()
        .id(1L)
        .massOperationExecution(execution)
        .itemKey("loan-001")
        .itemData("{\"principal\": 1000}")
        .status(MassOperationItemStatus.PENDING)
        .errorMessage(null)
        .startedAt(null)
        .completedAt(null)
        .retryOf(null)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getMassOperationExecution()).isEqualTo(execution);
    assertThat(entity.getItemKey()).isEqualTo("loan-001");
    assertThat(entity.getItemData()).isEqualTo("{\"principal\": 1000}");
    assertThat(entity.getStatus()).isEqualTo(MassOperationItemStatus.PENDING);
    assertThat(entity.getErrorMessage()).isNull();
    assertThat(entity.getStartedAt()).isNull();
    assertThat(entity.getCompletedAt()).isNull();
    assertThat(entity.getRetryOf()).isNull();
  }

  @Test
  @DisplayName("Should create MassOperationItemEntity via no-args constructor")
  void shouldCreateMassOperationItemEntityViaNoArgsConstructor() {
    var entity = new MassOperationItemEntity();
    assertThat(entity).isNotNull();
  }
}
