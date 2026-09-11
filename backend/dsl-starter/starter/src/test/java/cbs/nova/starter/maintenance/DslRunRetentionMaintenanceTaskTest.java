package cbs.nova.starter.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.service.DslRunRetentionPurger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DslRunRetentionMaintenanceTaskTest {

  @Mock
  private DslRunRepository runRepository;

  @Mock
  private ScheduledExecutorService executor;

  @Test
  void delegatesToPurgerAndReportsReturnedCount() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry,
            Duration.ofHours(24), Duration.ofMinutes(30), 100, executor,
            null, Clock.systemUTC());
    when(runRepository.purgeFinishedBefore(
            ArgumentMatchers.any(Instant.class), anyInt(),
            ArgumentMatchers.<Consumer<List<String>>>any()))
            .thenReturn(4);

    MaintenanceTask task = new DslRunRetentionMaintenanceTask(purger);

    assertThat(task.name()).isEqualTo(StarterConstants.RUN_RETENTION_TASK_NAME);
    MaintenanceResult result = task.run();
    assertThat(result.purged()).isEqualTo(4);
    assertThat(result.duration()).isNotNull();

    verify(runRepository).purgeFinishedBefore(ArgumentMatchers.any(Instant.class),
            anyInt(), ArgumentMatchers.<Consumer<List<String>>>any());
  }

  @Test
  void disabledPurgerReturnsZeroPurged() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry,
            Duration.ZERO, Duration.ofMinutes(5), 100, executor,
            null, Clock.systemUTC());

    MaintenanceTask task = new DslRunRetentionMaintenanceTask(purger);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    verify(runRepository, Mockito.never())
            .purgeFinishedBefore(ArgumentMatchers.any(Instant.class), anyInt(),
                    ArgumentMatchers.<Consumer<List<String>>>any());
  }
}
