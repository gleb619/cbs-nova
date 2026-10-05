package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.CbsRetentionProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.persistence.CompileDiagnosticCrudRepository;
import cbs.nova.starter.persistence.DslAuditCrudRepository;
import cbs.nova.starter.persistence.DslEventCrudRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DslAuxTableRetentionPurgerTest {

  @Mock
  private DslAuditCrudRepository auditRepository;

  @Mock
  private DslEventCrudRepository eventRepository;

  @Mock
  private CompileDiagnosticCrudRepository diagnosticRepository;

  @Mock
  private ScheduledExecutorService executor;

  private static CbsRetentionProperties disabledProperties() {
    return new CbsRetentionProperties();
  }

  private static CbsRetentionProperties.Audit audit(Duration retention, int batchSize,
          Duration interval) {
    CbsRetentionProperties.Audit a = new CbsRetentionProperties.Audit();
    a.setRetention(retention);
    a.setPurgeBatchSize(batchSize);
    a.setPurgeInterval(interval);
    return a;
  }

  private static CbsRetentionProperties.Events events(Duration retention, int batchSize,
          Duration interval) {
    CbsRetentionProperties.Events e = new CbsRetentionProperties.Events();
    e.setRetention(retention);
    e.setPurgeBatchSize(batchSize);
    e.setPurgeInterval(interval);
    return e;
  }

  private static CbsRetentionProperties.Diagnostics diagnostics(Duration retention, int batchSize,
          Duration interval) {
    CbsRetentionProperties.Diagnostics d = new CbsRetentionProperties.Diagnostics();
    d.setRetention(retention);
    d.setPurgeBatchSize(batchSize);
    d.setPurgeInterval(interval);
    return d;
  }

  @Test
  void disabledRetentionDoesNotScheduleAndPurgesNothing() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    DslAuxTableRetentionPurger purger = new DslAuxTableRetentionPurger(
            auditRepository, eventRepository, diagnosticRepository, meterRegistry,
            disabledProperties().getAudit(), disabledProperties().getEvents(),
            disabledProperties().getDiagnostics(), executor, Clock.systemUTC());

    purger.start();

    verify(executor, never()).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    assertThat(purger.purge()).isZero();
    assertThat(meterRegistry.find(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER).counter())
            .isNull();
    assertThat(meterRegistry.find(StarterConstants.EVENTS_PURGED_COUNTER).counter()).isNull();
    assertThat(meterRegistry.find(StarterConstants.DIAGNOSTICS_PURGED_COUNTER).counter()).isNull();
  }

  @Test
  void enabledAuditRetentionSchedulesAndPurgesOlderRows() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    Duration retention = Duration.ofHours(24);
    CbsRetentionProperties.Audit audit = audit(retention, 100, Duration.ofMinutes(30));
    CbsRetentionProperties props = disabledProperties();
    props.setAudit(audit);

    DslAuxTableRetentionPurger purger = new DslAuxTableRetentionPurger(
            auditRepository, eventRepository, diagnosticRepository, meterRegistry,
            props.getAudit(), props.getEvents(), props.getDiagnostics(), executor, clock);

    Instant expectedCutoff = clock.instant().minus(retention);
    when(auditRepository.deleteOlderThan(eq(expectedCutoff), eq(100))).thenReturn(3);

    purger.start();

    verify(executor).scheduleWithFixedDelay(
            any(Runnable.class),
            eq(Duration.ofMinutes(30).toMillis()),
            eq(Duration.ofMinutes(30).toMillis()),
            eq(TimeUnit.MILLISECONDS));

    int deleted = purger.purge();

    assertThat(deleted).isEqualTo(3);
    Counter counter = meterRegistry.find(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER)
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(3.0);
    verify(eventRepository, never()).deleteOlderThan(any(Instant.class), anyInt());
    verify(diagnosticRepository, never()).deleteOlderThan(any(Instant.class), anyInt());
  }

  @Test
  void enabledEventsRetentionUsesOwnCounterAndCutoff() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    Duration retention = Duration.ofDays(7);
    CbsRetentionProperties.Events events = events(retention, 50, Duration.ofHours(2));
    CbsRetentionProperties props = disabledProperties();
    props.setEvents(events);

    DslAuxTableRetentionPurger purger = new DslAuxTableRetentionPurger(
            auditRepository, eventRepository, diagnosticRepository, meterRegistry,
            props.getAudit(), props.getEvents(), props.getDiagnostics(), executor, clock);

    Instant expectedCutoff = clock.instant().minus(retention);
    when(eventRepository.deleteOlderThan(eq(expectedCutoff), eq(50))).thenReturn(5);

    purger.purge();

    assertThat(meterRegistry.find(StarterConstants.EVENTS_PURGED_COUNTER).counter().count())
            .isEqualTo(5.0);
    assertThat(meterRegistry.find(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER).counter())
            .isNull();
    assertThat(meterRegistry.find(StarterConstants.DIAGNOSTICS_PURGED_COUNTER).counter()).isNull();
  }

  @Test
  void enabledDiagnosticsRetentionLoopsUntilPartialBatch() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    Duration retention = Duration.ofDays(30);
    CbsRetentionProperties.Diagnostics diagnostics = diagnostics(retention, 100,
            Duration.ofMinutes(15));
    CbsRetentionProperties props = disabledProperties();
    props.setDiagnostics(diagnostics);

    DslAuxTableRetentionPurger purger = new DslAuxTableRetentionPurger(
            auditRepository, eventRepository, diagnosticRepository, meterRegistry,
            props.getAudit(), props.getEvents(), props.getDiagnostics(), executor, clock);

    Instant expectedCutoff = clock.instant().minus(retention);
    when(diagnosticRepository.deleteOlderThan(eq(expectedCutoff), eq(100)))
            .thenReturn(100)
            .thenReturn(37);

    int deleted = purger.purge();

    assertThat(deleted).isEqualTo(137);
    assertThat(meterRegistry.find(StarterConstants.DIAGNOSTICS_PURGED_COUNTER).counter().count())
            .isEqualTo(137.0);
  }

  @Test
  void zeroRowsPurgedDoesNotIncrementCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    CbsRetentionProperties props = disabledProperties();
    props.setEvents(events(Duration.ofHours(1), 100, Duration.ofMinutes(5)));

    DslAuxTableRetentionPurger purger = new DslAuxTableRetentionPurger(
            auditRepository, eventRepository, diagnosticRepository, meterRegistry,
            props.getAudit(), props.getEvents(), props.getDiagnostics(), executor, clock);

    when(eventRepository.deleteOlderThan(any(Instant.class), anyInt())).thenReturn(0);

    purger.start();
    int deleted = purger.purge();

    assertThat(deleted).isZero();
    assertThat(meterRegistry.find(StarterConstants.EVENTS_PURGED_COUNTER).counter()).isNull();
  }

  @Test
  void negativeRetentionIsTreatedAsDisabled() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    CbsRetentionProperties props = disabledProperties();
    props.setAudit(audit(Duration.ofDays(-1), 100, Duration.ofHours(1)));

    DslAuxTableRetentionPurger purger = new DslAuxTableRetentionPurger(
            auditRepository, eventRepository, diagnosticRepository, meterRegistry,
            props.getAudit(), props.getEvents(), props.getDiagnostics(), executor,
            Clock.systemUTC());

    purger.start();

    verify(executor, never()).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    assertThat(purger.purge()).isZero();
    verify(auditRepository, never()).deleteOlderThan(any(Instant.class), anyInt());
  }

  @Test
  void absentMeterRegistryIsTolerated() {
    Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    CbsRetentionProperties props = disabledProperties();
    props.setDiagnostics(diagnostics(Duration.ofDays(1), 50, Duration.ofMinutes(5)));

    DslAuxTableRetentionPurger purger = new DslAuxTableRetentionPurger(
            auditRepository, eventRepository, diagnosticRepository, /* meterRegistry */ null,
            props.getAudit(), props.getEvents(), props.getDiagnostics(), executor, clock);

    when(diagnosticRepository.deleteOlderThan(any(Instant.class), anyInt())).thenReturn(3);

    int deleted = purger.purge();

    assertThat(deleted).isEqualTo(3);
  }
}
