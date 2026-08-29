package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
class DslRunRetentionPurgerTest {

  @Mock
  private DslRunRepository runRepository;

  @Mock
  private ScheduledExecutorService executor;

  @Test
  void disabledRetentionDoesNotScheduleAndPurgesNothing() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry, Duration.ZERO, Duration.ofMinutes(1), 100, executor);

    purger.start();

    verify(executor, never()).scheduleWithFixedDelay(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any());
    assertThat(purger.purge()).isZero();
    assertThat(meterRegistry.find(DslRunRetentionPurger.PURGED_COUNTER).counter()).isNull();
  }

  @Test
  void enabledRetentionSchedulesAndPurgesOlderFinishedRuns() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    Duration retention = Duration.ofHours(24);
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry, retention, Duration.ofMinutes(30), 100, executor, clock);

    Instant expectedCutoff = clock.instant().minus(retention);
    when(runRepository.purgeFinishedBefore(expectedCutoff, 100)).thenReturn(3);

    purger.start();

    verify(executor).scheduleWithFixedDelay(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(30).toMillis()),
            org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(30).toMillis()),
            org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS));

    int deleted = purger.purge();

    assertThat(deleted).isEqualTo(3);
    Counter counter = meterRegistry.find(DslRunRetentionPurger.PURGED_COUNTER).counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(3.0);
    verify(runRepository).purgeFinishedBefore(expectedCutoff, 100);
  }

  @Test
  void zeroRowsPurgedDoesNotIncrementCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry, Duration.ofHours(1), Duration.ofMinutes(5), 100, executor,
            clock);

    when(runRepository.purgeFinishedBefore(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(100)))
            .thenReturn(0);

    purger.start();
    int deleted = purger.purge();

    assertThat(deleted).isZero();
    assertThat(meterRegistry.find(DslRunRetentionPurger.PURGED_COUNTER).counter()).isNull();
  }
}
