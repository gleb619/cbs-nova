package cbs.nova;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.entity.MassOperationItemEntity;
import cbs.nova.entity.MassOperationItemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class MassOperationItemEntityTest {

  @Test
  @DisplayName("Should create MassOperationItemEntity via builder with all fields")
  void shouldCreateMassOperationItemEntityViaBuilderWithAllFields() {
    var now = OffsetDateTime.now();

    var entity = MassOperationItemEntity.builder()
        .id(1L)
        .massOperationExecutionId(1L)
        .itemKey("loan-001")
        .itemData("{\"principal\": 1000}")
        .status(MassOperationItemStatus.PENDING)
        .errorMessage(null)
        .startedAt(null)
        .completedAt(null)
        .retryOfId(null)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getMassOperationExecutionId()).isEqualTo(1L);
    assertThat(entity.getItemKey()).isEqualTo("loan-001");
    assertThat(entity.getItemData()).isEqualTo("{\"principal\": 1000}");
    assertThat(entity.getStatus()).isEqualTo(MassOperationItemStatus.PENDING);
    assertThat(entity.getErrorMessage()).isNull();
    assertThat(entity.getStartedAt()).isNull();
    assertThat(entity.getCompletedAt()).isNull();
    assertThat(entity.getRetryOfId()).isNull();
  }

  @Test
  @DisplayName("Should create MassOperationItemEntity via no-args constructor")
  void shouldCreateMassOperationItemEntityViaNoArgsConstructor() {
    var entity = new MassOperationItemEntity();
    assertThat(entity).isNotNull();
  }
}
