package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.TransactionExecutionRepository;
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
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
class DslRunRetentionPurgerTest {

  @Mock
  private DslRunRepository runRepository;

  @Mock
  private TransactionExecutionRepository transactionExecutionRepository;

  @Mock
  private ScheduledExecutorService executor;

  @Test
  void disabledRetentionDoesNotScheduleAndPurgesNothing() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry, Duration.ZERO, Duration.ofMinutes(1), 100, executor);

    purger.start();

    verify(executor, never()).scheduleWithFixedDelay(
            any(),
            anyLong(),
            anyLong(),
            any());
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
    when(runRepository.purgeFinishedBefore(eq(expectedCutoff), eq(100), any(Consumer.class)))
            .thenAnswer(invocation -> {
              Consumer<List<String>> consumer = invocation.getArgument(2);
              consumer.accept(List.of("run-1", "run-2", "run-3"));
              return 3;
            });

    purger.start();

    verify(executor).scheduleWithFixedDelay(
            any(Runnable.class),
            eq(Duration.ofMinutes(30).toMillis()),
            eq(Duration.ofMinutes(30).toMillis()),
            eq(TimeUnit.MILLISECONDS));

    int deleted = purger.purge();

    assertThat(deleted).isEqualTo(3);
    Counter counter = meterRegistry.find(DslRunRetentionPurger.PURGED_COUNTER).counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(3.0);
    verify(runRepository).purgeFinishedBefore(eq(expectedCutoff), eq(100), any(Consumer.class));
    assertThat(meterRegistry.find(DslRunRetentionPurger.TRANSACTIONS_PURGED_COUNTER).counter())
            .isNull();
  }

  @Test
  void zeroRowsPurgedDoesNotIncrementCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry, Duration.ofHours(1), Duration.ofMinutes(5), 100, executor,
            clock);

    when(runRepository.purgeFinishedBefore(any(Instant.class), eq(100), any(Consumer.class)))
            .thenReturn(0);

    purger.start();
    int deleted = purger.purge();

    assertThat(deleted).isZero();
    assertThat(meterRegistry.find(DslRunRetentionPurger.PURGED_COUNTER).counter()).isNull();
    assertThat(meterRegistry.find(DslRunRetentionPurger.TRANSACTIONS_PURGED_COUNTER).counter())
            .isNull();
  }

  @Test
  void purgeDeletesChildTransactionsAndIncrementsCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry, Duration.ofHours(1), Duration.ofMinutes(5), 100, executor,
            transactionExecutionRepository, clock);

    List<String> ids = List.of("run-1", "run-2");
    when(runRepository.purgeFinishedBefore(any(Instant.class), eq(100), any(Consumer.class)))
            .thenAnswer(invocation -> {
              Consumer<List<String>> consumer = invocation.getArgument(2);
              consumer.accept(ids);
              return 2;
            });
    when(transactionExecutionRepository.deleteByRunIds(ids)).thenReturn(5);

    int deleted = purger.purge();

    assertThat(deleted).isEqualTo(2);
    assertThat(meterRegistry.find(DslRunRetentionPurger.PURGED_COUNTER).counter().count())
            .isEqualTo(2.0);
    assertThat(meterRegistry.find(DslRunRetentionPurger.TRANSACTIONS_PURGED_COUNTER).counter())
            .isNotNull();
    assertThat(
            meterRegistry.find(DslRunRetentionPurger.TRANSACTIONS_PURGED_COUNTER).counter().count())
            .isEqualTo(5.0);
    verify(transactionExecutionRepository).deleteByRunIds(ids);
  }

  @Test
  void purgeWithNullTransactionRepositoryDoesNotNpe() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslRunRetentionPurger purger = new DslRunRetentionPurger(
            runRepository, meterRegistry, Duration.ofHours(1), Duration.ofMinutes(5), 100, executor,
            clock);

    when(runRepository.purgeFinishedBefore(any(Instant.class), eq(100), any(Consumer.class)))
            .thenAnswer(invocation -> {
              Consumer<List<String>> consumer = invocation.getArgument(2);
              consumer.accept(List.of("run-1"));
              return 1;
            });

    int deleted = purger.purge();

    assertThat(deleted).isEqualTo(1);
    assertThat(meterRegistry.find(DslRunRetentionPurger.PURGED_COUNTER).counter().count())
            .isEqualTo(1.0);
    assertThat(meterRegistry.find(DslRunRetentionPurger.TRANSACTIONS_PURGED_COUNTER).counter())
            .isNull();
  }
}
