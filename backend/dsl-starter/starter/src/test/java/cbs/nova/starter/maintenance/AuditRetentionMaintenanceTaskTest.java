package cbs.nova.starter.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.DslAuditRetentionProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.persistence.DslAuditStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditRetentionMaintenanceTaskTest {

  @Mock
  private DslAuditStore auditStore;

  @Test
  void exposesStableName() {
    AuditRetentionMaintenanceTask task = newTask(false, Duration.ofDays(90), 100,
            Clock.systemUTC());

    assertThat(task.name()).isEqualTo(StarterConstants.AUDIT_RETENTION_TASK_NAME);
  }

  @Test
  void disabledTaskIsNoOpAndDoesNotTouchStore() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslAuditRetentionProperties properties = newProperties(false, Duration.ofDays(90), 100);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, meterRegistry, clock);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    assertThat(result.duration()).isNotNull();
    verify(auditStore, never()).purgeOlderThan(any(Instant.class), anyInt());
    assertThat(meterRegistry.find(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER).counter())
            .isNull();
  }

  @Test
  void enabledTaskPurgesOlderRowsAndReportsTotal() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    Duration retention = Duration.ofDays(90);
    DslAuditRetentionProperties properties = newProperties(true, retention, 100);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, meterRegistry, clock);
    Instant expectedCutoff = clock.instant().minus(retention);
    when(auditStore.purgeOlderThan(expectedCutoff, 100)).thenReturn(42);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isEqualTo(42);
    assertThat(result.duration()).isNotNull();
    verify(auditStore, times(1)).purgeOlderThan(expectedCutoff, 100);
    assertThat(meterRegistry.find(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER).counter()
            .count()).isEqualTo(42.0);
  }

  @Test
  void enabledTaskRepeatsBatchUntilPartialBatch() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    Duration retention = Duration.ofDays(30);
    DslAuditRetentionProperties properties = newProperties(true, retention, 100);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, meterRegistry, clock);
    Instant expectedCutoff = clock.instant().minus(retention);
    // Two full batches followed by a partial batch — task must stop and not loop forever.
    when(auditStore.purgeOlderThan(expectedCutoff, 100))
            .thenReturn(100)
            .thenReturn(100)
            .thenReturn(37);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isEqualTo(237);
    verify(auditStore, times(3)).purgeOlderThan(expectedCutoff, 100);
    assertThat(meterRegistry.find(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER).counter()
            .count()).isEqualTo(237.0);
  }

  @Test
  void enabledTaskStopsOnEmptyBatch() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslAuditRetentionProperties properties = newProperties(true, Duration.ofDays(7), 50);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, meterRegistry, clock);
    when(auditStore.purgeOlderThan(any(Instant.class), anyInt())).thenReturn(0);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    verify(auditStore, times(1)).purgeOlderThan(any(Instant.class), anyInt());
    assertThat(meterRegistry.find(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER).counter())
            .isNull();
  }

  @Test
  void enabledTaskSwallowsStoreFailuresAndReturnsZero() {
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslAuditRetentionProperties properties = newProperties(true, Duration.ofDays(7), 50);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, new SimpleMeterRegistry(), clock);
    when(auditStore.purgeOlderThan(any(Instant.class), anyInt()))
            .thenThrow(new RuntimeException("boom"));

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    assertThat(result.duration()).isNotNull();
  }

  @Test
  void zeroRowsDoNotIncrementCounterEvenWhenEnabled() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslAuditRetentionProperties properties = newProperties(true, Duration.ofDays(1), 100);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, meterRegistry, clock);
    when(auditStore.purgeOlderThan(any(Instant.class), anyInt())).thenReturn(0);

    task.run();

    assertThat(meterRegistry.find(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER).counter())
            .isNull();
  }

  @Test
  void enabledTaskWithZeroMaxAgeIsNoOp() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslAuditRetentionProperties properties = newProperties(true, Duration.ZERO, 100);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, meterRegistry, clock);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    verify(auditStore, never()).purgeOlderThan(any(Instant.class), anyInt());
  }

  @Test
  void enabledTaskWithNegativeMaxAgeIsNoOp() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslAuditRetentionProperties properties = newProperties(true, Duration.ofDays(-1), 50);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, meterRegistry, clock);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    verify(auditStore, never()).purgeOlderThan(any(Instant.class), anyInt());
  }

  @Test
  void absentMeterRegistryIsTolerated() {
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslAuditRetentionProperties properties = newProperties(true, Duration.ofDays(1), 50);
    AuditRetentionMaintenanceTask task = new AuditRetentionMaintenanceTask(
            auditStore, properties, /* meterRegistry */ null, clock);
    when(auditStore.purgeOlderThan(any(Instant.class), anyInt())).thenReturn(3);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isEqualTo(3);
  }

  private static AuditRetentionMaintenanceTask newTask(
          boolean enabled, Duration maxAge, int batchSize, Clock clock) {
    return new AuditRetentionMaintenanceTask(
            /* store */ null,
            newProperties(enabled, maxAge, batchSize),
            new SimpleMeterRegistry(),
            clock);
  }

  private static DslAuditRetentionProperties newProperties(
          boolean enabled, Duration maxAge, int batchSize) {
    DslAuditRetentionProperties props = new DslAuditRetentionProperties();
    props.setEnabled(enabled);
    props.setMaxAge(maxAge);
    props.setBatchSize(batchSize);
    return props;
  }
}
