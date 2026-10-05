package cbs.nova.starter.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.DslWebhookDeliveryRetentionProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.webhook.WebhookDeliveryRecordRepository;
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
class WebhookDeliveryRetentionMaintenanceTaskTest {

  @Mock
  private WebhookDeliveryRecordRepository repository;

  @Test
  void exposesStableName() {
    WebhookDeliveryRetentionMaintenanceTask task = newTask(false, Duration.ofDays(30), 100,
            Clock.systemUTC());

    assertThat(task.name()).isEqualTo(StarterConstants.WEBHOOK_DELIVERY_RETENTION_TASK_NAME);
  }

  @Test
  void disabledTaskIsNoOpAndDoesNotTouchRepository() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslWebhookDeliveryRetentionProperties properties = newProperties(false, Duration.ofDays(30),
            100);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, meterRegistry, clock);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    assertThat(result.duration()).isNotNull();
    verify(repository, never()).purgeOlderThan(any(Instant.class), anyInt());
    assertThat(meterRegistry
            .find(StarterConstants.WEBHOOK_DELIVERY_RETENTION_PURGED_COUNTER).counter())
            .isNull();
  }

  @Test
  void enabledTaskPurgesOlderRowsAndReportsTotal() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    Duration retention = Duration.ofDays(30);
    DslWebhookDeliveryRetentionProperties properties = newProperties(true, retention, 100);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, meterRegistry, clock);
    Instant expectedCutoff = clock.instant().minus(retention);
    when(repository.purgeOlderThan(expectedCutoff, 100)).thenReturn(42);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isEqualTo(42);
    assertThat(result.duration()).isNotNull();
    verify(repository, times(1)).purgeOlderThan(expectedCutoff, 100);
    assertThat(meterRegistry
            .find(StarterConstants.WEBHOOK_DELIVERY_RETENTION_PURGED_COUNTER).counter()
            .count()).isEqualTo(42.0);
  }

  @Test
  void enabledTaskRepeatsBatchUntilPartialBatch() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    Duration retention = Duration.ofDays(14);
    DslWebhookDeliveryRetentionProperties properties = newProperties(true, retention, 100);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, meterRegistry, clock);
    Instant expectedCutoff = clock.instant().minus(retention);
    when(repository.purgeOlderThan(expectedCutoff, 100))
            .thenReturn(100)
            .thenReturn(100)
            .thenReturn(37);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isEqualTo(237);
    verify(repository, times(3)).purgeOlderThan(expectedCutoff, 100);
    assertThat(meterRegistry
            .find(StarterConstants.WEBHOOK_DELIVERY_RETENTION_PURGED_COUNTER).counter()
            .count()).isEqualTo(237.0);
  }

  @Test
  void enabledTaskStopsOnEmptyBatch() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslWebhookDeliveryRetentionProperties properties = newProperties(true, Duration.ofDays(7), 50);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, meterRegistry, clock);
    when(repository.purgeOlderThan(any(Instant.class), anyInt())).thenReturn(0);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    verify(repository, times(1)).purgeOlderThan(any(Instant.class), anyInt());
    assertThat(meterRegistry
            .find(StarterConstants.WEBHOOK_DELIVERY_RETENTION_PURGED_COUNTER).counter())
            .isNull();
  }

  @Test
  void enabledTaskSwallowsRepositoryFailuresAndReturnsZero() {
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslWebhookDeliveryRetentionProperties properties = newProperties(true, Duration.ofDays(7), 50);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, new SimpleMeterRegistry(), clock);
    when(repository.purgeOlderThan(any(Instant.class), anyInt()))
            .thenThrow(new RuntimeException("boom"));

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    assertThat(result.duration()).isNotNull();
  }

  @Test
  void zeroRowsDoNotIncrementCounterEvenWhenEnabled() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslWebhookDeliveryRetentionProperties properties = newProperties(true, Duration.ofDays(1), 100);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, meterRegistry, clock);
    when(repository.purgeOlderThan(any(Instant.class), anyInt())).thenReturn(0);

    task.run();

    assertThat(meterRegistry
            .find(StarterConstants.WEBHOOK_DELIVERY_RETENTION_PURGED_COUNTER).counter())
            .isNull();
  }

  @Test
  void enabledTaskWithZeroMaxAgeIsNoOp() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslWebhookDeliveryRetentionProperties properties = newProperties(true, Duration.ZERO, 100);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, meterRegistry, clock);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    verify(repository, never()).purgeOlderThan(any(Instant.class), anyInt());
  }

  @Test
  void enabledTaskWithNegativeMaxAgeIsNoOp() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslWebhookDeliveryRetentionProperties properties = newProperties(true, Duration.ofDays(-1), 50);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, meterRegistry, clock);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isZero();
    verify(repository, never()).purgeOlderThan(any(Instant.class), anyInt());
  }

  @Test
  void absentMeterRegistryIsTolerated() {
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    DslWebhookDeliveryRetentionProperties properties = newProperties(true, Duration.ofDays(1), 50);
    WebhookDeliveryRetentionMaintenanceTask task = new WebhookDeliveryRetentionMaintenanceTask(
            repository, properties, /* meterRegistry */ null, clock);
    when(repository.purgeOlderThan(any(Instant.class), anyInt())).thenReturn(3);

    MaintenanceResult result = task.run();

    assertThat(result.purged()).isEqualTo(3);
  }

  private static WebhookDeliveryRetentionMaintenanceTask newTask(
          boolean enabled, Duration maxAge, int batchSize, Clock clock) {
    return new WebhookDeliveryRetentionMaintenanceTask(
            /* repository */ null,
            newProperties(enabled, maxAge, batchSize),
            new SimpleMeterRegistry(),
            clock);
  }

  private static DslWebhookDeliveryRetentionProperties newProperties(
          boolean enabled, Duration maxAge, int batchSize) {
    DslWebhookDeliveryRetentionProperties props = new DslWebhookDeliveryRetentionProperties();
    props.setEnabled(enabled);
    props.setMaxAge(maxAge);
    props.setBatchSize(batchSize);
    return props;
  }
}
