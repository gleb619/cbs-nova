package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.persistence.NotificationRuleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Micrometer metrics coverage for {@link NotificationRuleEngine}. Verifies the
 * {@code cbs.notification.firing.count{sink,outcome}} counter and
 * {@code cbs.notification.firing.duration{sink}} timer are recorded with bounded cardinality:
 * {@code sink} = sink type string, {@code outcome} = closed vocabulary from {@link FiringOutcome}
 * (success / failure / noop / skipped / rejected). Rule names, URLs and run ids never appear as
 * tags.
 */
class NotificationRuleEngineMetricsTest {

  private NotificationRuleRepository ruleRepository;
  private NotificationRuleFiringRepository firingRepository;
  private NotificationRuleService ruleService;
  private NotificationSink webhookSink;
  private NotificationSink emailSink;
  private MeterRegistry registry;

  @BeforeEach
  void setUp() {
    ruleRepository = mock(NotificationRuleRepository.class);
    firingRepository = mock(NotificationRuleFiringRepository.class);
    ruleService = mock(NotificationRuleService.class);
    webhookSink = mock(NotificationSink.class);
    emailSink = mock(NotificationSink.class);
    when(webhookSink.sinkType()).thenReturn("webhook");
    when(emailSink.sinkType()).thenReturn("email");
    registry = new SimpleMeterRegistry();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    if (synchronousExecutor != null) {
      awaitExecutorDrain();
    }
  }

  private NotificationRuleEngine engine(NotificationSink... sinks) {
    return new NotificationRuleEngine(ruleRepository, firingRepository, ruleService,
            List.of(sinks), synchronousExecutor(), registry);
  }

  /**
   * Single-thread executor that the test shuts down at the end of each test so the queued
   * fire-and-record tasks are guaranteed to run before assertions. CallerRuns + queueCapacity=0
   * means every task runs on the submitter thread unless the worker is busy, but {@code await}
   * below covers the worker-busy case (e.g. multiple sinks).
   */
  private ThreadPoolTaskExecutor synchronousExecutor;

  private ThreadPoolTaskExecutor synchronousExecutor() {
    if (synchronousExecutor == null) {
      synchronousExecutor = new ThreadPoolTaskExecutor();
      synchronousExecutor.setCorePoolSize(1);
      synchronousExecutor.setMaxPoolSize(1);
      synchronousExecutor.setQueueCapacity(0);
      synchronousExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
      synchronousExecutor.setThreadNamePrefix("test-notif-");
      synchronousExecutor.initialize();
    }
    return synchronousExecutor;
  }

  private void awaitExecutorDrain() throws InterruptedException {
    ThreadPoolExecutor tpe = synchronousExecutor.getThreadPoolExecutor();
    // Caller-runs means tasks already ran inline; only the worker-busy case is left to await.
    tpe.shutdown();
    tpe.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
  }

  @Test
  void successFiringIncrementsSuccessSeriesPerSink() throws Exception {
    NotificationRuleEngine e = engine(webhookSink);
    NotificationRuleEntity rule = rule(1L, "rule-1", "RunFailed", null);
    NotificationActionDto action = new NotificationActionDto("webhook", "http://x.example",
            null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(webhookSink.deliver(eq(rule), eq(action), any()))
            .thenReturn(FiringOutcome.success("ok", 42L));

    e.onEvent(runFailed("run-1", "Demo"), 100L);
    awaitExecutorDrain();

    verify(firingRepository, times(1)).insert(any());
    assertCounter("webhook", "success", 1.0);
    assertTimerCount("webhook", 1);
  }

  @Test
  void failureFiringIncrementsFailureSeries() throws Exception {
    NotificationRuleEngine e = engine(webhookSink);
    NotificationRuleEntity rule = rule(2L, "rule-2", "RunFailed", null);
    NotificationActionDto action = new NotificationActionDto("webhook", "http://x.example",
            null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(webhookSink.deliver(eq(rule), eq(action), any()))
            .thenReturn(FiringOutcome.failure("sink exploded", 7L));

    e.onEvent(runFailed("run-2", "Demo"), 101L);
    awaitExecutorDrain();

    verify(firingRepository, times(1)).insert(any());
    assertCounter("webhook", "failure", 1.0);
    assertTimerCount("webhook", 1);
  }

  @Test
  void rejectedFiringIncrementsRejectedSeries() throws Exception {
    NotificationRuleEngine e = engine(webhookSink);
    NotificationRuleEntity rule = rule(3L, "rule-3", "RunFailed", null);
    NotificationActionDto action = new NotificationActionDto("webhook", "http://x.example",
            null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(webhookSink.deliver(eq(rule), eq(action), any()))
            .thenReturn(FiringOutcome.rejected("non-https", 1L));

    e.onEvent(runFailed("run-3", "Demo"), 102L);
    awaitExecutorDrain();

    verify(firingRepository, times(1)).insert(any());
    assertCounter("webhook", "rejected", 1.0);
    assertTimerCount("webhook", 1);
  }

  @Test
  void unknownSinkRecordsSkippedAndStillMeters() throws Exception {
    NotificationRuleEngine e = engine(); // no sinks
    NotificationRuleEntity rule = rule(4L, "rule-4", "RunFailed", null);
    NotificationActionDto action = new NotificationActionDto("webhook", "http://x.example",
            null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));

    e.onEvent(runFailed("run-4", "Demo"), 103L);
    awaitExecutorDrain();

    verify(firingRepository, times(1)).insert(any());
    assertCounter("webhook", "skipped", 1.0);
    assertTimerCount("webhook", 1);
  }

  @Test
  void multipleSinksUseDifferentSinkTags() throws Exception {
    NotificationRuleEngine e = engine(webhookSink, emailSink);
    NotificationRuleEntity ruleWebhook = rule(5L, "rule-wh", "RunFailed", null);
    NotificationRuleEntity ruleEmail = rule(6L, "rule-em", "RunFailed", null);
    NotificationActionDto webhookAction = new NotificationActionDto("webhook", "http://x.example",
            null, null);
    NotificationActionDto emailAction = new NotificationActionDto("email", null, null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed"))
            .thenReturn(List.of(ruleWebhook, ruleEmail));
    when(ruleService.actionsOf(ruleWebhook)).thenReturn(List.of(webhookAction));
    when(ruleService.actionsOf(ruleEmail)).thenReturn(List.of(emailAction));
    when(webhookSink.deliver(eq(ruleWebhook), eq(webhookAction), any()))
            .thenReturn(FiringOutcome.success("ok", 1L));
    when(emailSink.deliver(eq(ruleEmail), eq(emailAction), any()))
            .thenReturn(FiringOutcome.success("ok", 2L));

    e.onEvent(runFailed("run-5", "Demo"), 104L);
    awaitExecutorDrain();

    verify(firingRepository, times(2)).insert(any());
    assertCounter("webhook", "success", 1.0);
    assertCounter("email", "success", 1.0);
    assertTimerCount("webhook", 1);
    assertTimerCount("email", 1);
  }

  @Test
  void firingFailureStillMetersButAuditInsertIsSkipped() throws Exception {
    NotificationRuleEngine e = engine(webhookSink);
    NotificationRuleEntity rule = rule(7L, "rule-7", "RunFailed", null);
    NotificationActionDto action = new NotificationActionDto("webhook", "http://x.example",
            null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(webhookSink.deliver(eq(rule), eq(action), any()))
            .thenReturn(FiringOutcome.failure("sink down", 3L));
    when(firingRepository.insert(any())).thenThrow(new RuntimeException("db down"));

    e.onEvent(runFailed("run-6", "Demo"), 105L);
    awaitExecutorDrain();

    // Audit row failed, but the meter still increments — metrics are the source of truth.
    verify(firingRepository, times(1)).insert(any());
    assertCounter("webhook", "failure", 1.0);
    assertTimerCount("webhook", 1);
  }

  @Test
  void unmatchedEventTypeDoesNotRecordMetric() {
    NotificationRuleEngine e = engine(webhookSink);
    when(ruleRepository.findMatchingEnabled("RunCompleted")).thenReturn(List.of());

    e.onEvent(runCompleted("run-7", "Demo"), 106L);

    verify(firingRepository, never()).insert(any());
    assertThat(registry.find("cbs.notification.firing.count").counter()).isNull();
    assertThat(registry.find("cbs.notification.firing.duration").timer()).isNull();
  }

  @Test
  void tagCardinalityIsBoundedToSinkAndOutcomeOnly() throws Exception {
    NotificationRuleEngine e = engine(webhookSink);
    NotificationRuleEntity rule = rule(8L, "rule-with-very-specific-name",
            "RunFailed", null);
    NotificationActionDto action = new NotificationActionDto("webhook",
            "https://example.invalid/very-secret-url/very-long", null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(webhookSink.deliver(eq(rule), eq(action), any()))
            .thenReturn(FiringOutcome.success("ok", 1L));

    e.onEvent(runFailed("run-with-uuid-pii-xyz", "MyProcess-Definition"), 107L);
    awaitExecutorDrain();

    var counter = registry.find("cbs.notification.firing.count").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.getId().getTags())
            .extracting(Tag::getKey)
            .containsExactlyInAnyOrder("sink", "outcome");
    assertThat(counter.getId().getTag("sink")).isEqualTo("webhook");
    assertThat(counter.getId().getTag("outcome")).isEqualTo("success");
    assertThat(counter.getId().getTag("ruleName")).isNull();
    assertThat(counter.getId().getTag("url")).isNull();
    assertThat(counter.getId().getTag("runId")).isNull();

    var timer = registry.find("cbs.notification.firing.duration").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.getId().getTags())
            .extracting(Tag::getKey)
            .containsExactly("sink");
  }

  @Test
  void registryAbsentStaysWorkingWithoutRecordingMeters() throws Exception {
    // 5-arg ctor with no registry, mirroring the existing NotificationRuleEngineTest wiring.
    NotificationRuleEngine e = new NotificationRuleEngine(ruleRepository, firingRepository,
            ruleService, List.of(webhookSink), synchronousExecutor());
    NotificationRuleEntity rule = rule(9L, "rule-9", "RunFailed", null);
    NotificationActionDto action = new NotificationActionDto("webhook", "http://x.example",
            null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(webhookSink.deliver(eq(rule), eq(action), any()))
            .thenReturn(FiringOutcome.success("ok", 1L));

    e.onEvent(runFailed("run-8", "Demo"), 108L);
    awaitExecutorDrain();

    verify(firingRepository, times(1)).insert(any());
    // No registry attached → this separate registry does not see the meter.
    assertThat(registry.find("cbs.notification.firing.count").counter()).isNull();
  }

  private void assertCounter(String sink, String outcome, double expected) {
    var counter = registry.find("cbs.notification.firing.count")
            .tag("sink", sink)
            .tag("outcome", outcome)
            .counter();
    assertThat(counter).as("counter for sink=%s outcome=%s", sink, outcome).isNotNull();
    assertThat(counter.count()).isEqualTo(expected);
  }

  private void assertTimerCount(String sink, long expected) {
    var timer = registry.find("cbs.notification.firing.duration")
            .tag("sink", sink)
            .timer();
    assertThat(timer).as("timer for sink=%s", sink).isNotNull();
    assertThat(timer.count()).isEqualTo(expected);
  }

  private static NotificationRuleEntity rule(Long id, String name, String eventType,
          String status) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(id, name, true, eventType, null, null, null, status,
            "[]", 0, "default", now, now);
  }

  private static DomainEvent.RunFailed runFailed(String runId, String processName) {
    Instant now = Instant.now();
    return new DomainEvent.RunFailed(runId, processName, DslRunStatus.FAILED, "boom", now, now,
            Instant.now(), null);
  }

  private static DomainEvent.RunCompleted runCompleted(String runId, String processName) {
    Instant now = Instant.now();
    return new DomainEvent.RunCompleted(runId, processName, DslRunStatus.COMPLETED, null, null,
            now, now, Instant.now(), null);
  }
}
