package cbs.nova.starter.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.starter.service.DslRunReconciliationService;
import org.junit.jupiter.api.Test;

class DslRunReconciliationMaintenanceTaskTest {

  @Test
  void delegatesToServiceAndReportsZeroPurged() {
    DslRunReconciliationService service = mock(DslRunReconciliationService.class);

    MaintenanceTask task = new DslRunReconciliationMaintenanceTask(service);

    assertThat(task.name()).isEqualTo(DslRunReconciliationMaintenanceTask.NAME);
    MaintenanceResult result = task.run();
    assertThat(result.purged()).isZero();
    assertThat(result.duration()).isNotNull();

    // reconciliation does not delete rows; the result count is 0 regardless
    // of state. The service method was actually invoked.
    verify(service).reconcileOnce();
  }
}
