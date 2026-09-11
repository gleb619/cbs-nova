package cbs.nova.starter.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.StarterConstants;
import org.junit.jupiter.api.Test;

class AuditRetentionMaintenanceTaskTest {

  @Test
  void runReturnsEmptyResultAndExposesStableName() {
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask();

    assertThat(task.name()).isEqualTo(StarterConstants.AUDIT_RETENTION_TASK_NAME);
    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    assertThat(result.duration()).isNotNull();
  }
}
