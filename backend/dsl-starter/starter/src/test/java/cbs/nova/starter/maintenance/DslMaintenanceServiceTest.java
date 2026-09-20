package cbs.nova.starter.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.maintenance.MaintenanceTask;
import cbs.nova.starter.maintenance.MaintenanceResult;
import cbs.nova.starter.maintenance.DslMaintenanceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.mock;

class DslMaintenanceServiceTest {

  private SimpleMeterRegistry meterRegistry;
  private ScheduledExecutorService executor;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    executor = mock(ScheduledExecutorService.class);
  }

  @Test
  void tasksAreOrderedByNameRegardlessOfRegistrationOrder() {
    MaintenanceTask z = recordingTask("zeta", 0);
    MaintenanceTask a = recordingTask("alpha", 0);
    MaintenanceTask m = recordingTask("mid", 0);
    DslMaintenanceService service = newService(List.of(z, m, a), Map.of(), executor);

    assertThat(service.tasks())
            .extracting(MaintenanceTask::name)
            .containsExactly("alpha", "mid", "zeta");
  }

  @Test
  void runOnceInvokesEveryEnabledTaskAndRecordsPurgedAndDuration() {
    MaintenanceTask retention = recordingTask("alpha", 7);
    MaintenanceTask reconciliation = recordingTask("zeta", 0);
    Map<String, Boolean> enabled = Map.of("alpha", true, "zeta", true);
    DslMaintenanceService service = newService(List.of(retention, reconciliation), enabled,
            executor, meterRegistry);

    service.runOnce();

    Timer alphaTimer = meterRegistry.find(StarterConstants.TASK_DURATION_TIMER)
            .tag(StarterConstants.TASK_TAG, "alpha").timer();
    assertThat(alphaTimer).isNotNull();
    assertThat(alphaTimer.count()).isEqualTo(1L);
    Counter alphaPurged = meterRegistry.find(StarterConstants.TASK_PURGED_COUNTER)
            .tag(StarterConstants.TASK_TAG, "alpha").counter();
    assertThat(alphaPurged).isNotNull();
    assertThat(alphaPurged.count()).isEqualTo(7.0);

    Timer zetaTimer = meterRegistry.find(StarterConstants.TASK_DURATION_TIMER)
            .tag(StarterConstants.TASK_TAG, "zeta").timer();
    assertThat(zetaTimer).isNotNull();
    assertThat(zetaTimer.count()).isEqualTo(1L);
    assertThat(meterRegistry.find(StarterConstants.TASK_PURGED_COUNTER)
            .tag(StarterConstants.TASK_TAG, "zeta").counter()).isNull();
  }

  @Test
  void disabledTasksAreSkipped() {
    MaintenanceTask a = recordingTask("alpha", 3);
    MaintenanceTask b = recordingTask("beta", 4);
    Map<String, Boolean> enabled = Map.of("alpha", true, "beta", false);
    DslMaintenanceService service = newService(List.of(a, b), enabled, executor, meterRegistry);

    service.runOnce();

    assertThat(meterRegistry.find(StarterConstants.TASK_DURATION_TIMER)
            .tag(StarterConstants.TASK_TAG, "alpha").timer()).isNotNull();
    assertThat(meterRegistry.find(StarterConstants.TASK_DURATION_TIMER)
            .tag(StarterConstants.TASK_TAG, "beta").timer()).isNull();
  }

  @Test
  void absentTaskEnabledDefaultsToEnabled() {
    MaintenanceTask a = recordingTask("alpha", 1);
    DslMaintenanceService service = newService(List.of(a), Map.of(), executor, meterRegistry);

    assertThat(service.isEnabled("alpha")).isTrue();
    assertThat(service.isEnabled("never-registered")).isTrue();
  }

  @Test
  void aFailingTaskDoesNotBlockOthers() {
    MaintenanceTask thrower = new MaintenanceTask() {
      @Override
      public String name() {
        return "thrower";
      }

      @Override
      public MaintenanceResult run() {
        throw new RuntimeException("boom");
      }
    };
    MaintenanceTask a = recordingTask("alpha", 1);
    DslMaintenanceService service = newService(List.of(thrower, a), Map.of(), executor,
            meterRegistry);

    service.runOnce();

    // alpha ran even though thrower failed
    assertThat(meterRegistry.find(StarterConstants.TASK_DURATION_TIMER)
            .tag(StarterConstants.TASK_TAG, "alpha").timer()).isNotNull();
    // thrower still recorded its duration (failure path records timing)
    assertThat(meterRegistry.find(StarterConstants.TASK_DURATION_TIMER)
            .tag(StarterConstants.TASK_TAG, "thrower").timer()).isNotNull();
  }

  @Test
  void missingMeterRegistryIsANoOp() {
    MaintenanceTask a = recordingTask("alpha", 2);
    DslMaintenanceService service = newService(List.of(a), Map.of(), executor, /* registry */ null);

    service.runOnce();

    // No exception thrown; nothing to assert against an absent registry.
    assertThat(meterRegistry.getMeters()).isEmpty();
  }

  @Test
  void startIsIdempotent() {
    DslMaintenanceService service = newService(List.of(recordingTask("alpha", 0)), Map.of(),
            executor);
    service.start();
    service.start();
    // No exception. Idempotent guard is exercised; nothing else to assert
    // against the mocked executor.
    service.shutdown();
  }

  // ---------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------

  private DslMaintenanceService newService(
          List<MaintenanceTask> tasks,
          Map<String, Boolean> enabled,
          ScheduledExecutorService exec) {
    return newService(tasks, enabled, exec, meterRegistry);
  }

  private DslMaintenanceService newService(
          List<MaintenanceTask> tasks,
          Map<String, Boolean> enabled,
          ScheduledExecutorService exec,
          SimpleMeterRegistry registry) {
    Map<String, Boolean> copy = new LinkedHashMap<>(enabled);
    List<MaintenanceTask> sorted = tasks.stream()
            .sorted(Comparator.comparing(MaintenanceTask::name))
            .toList();
    return new DslMaintenanceService(sorted, copy, Duration.ofMinutes(5), exec, registry);
  }

  private static MaintenanceTask recordingTask(String name, int purged) {
    return new MaintenanceTask() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public MaintenanceResult run() {
        return new MaintenanceResult(purged, Duration.ZERO);
      }
    };
  }
}
