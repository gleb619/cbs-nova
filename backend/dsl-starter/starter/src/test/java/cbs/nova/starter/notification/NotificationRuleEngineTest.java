package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.FireResultDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.model.NotificationRuleModels.TestEventResponse;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.persistence.NotificationRuleRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unit specs for {@link NotificationRuleEngine}. Pure Mockito: repository, service, sinks and
 * executor are mocked so the tests isolate matching semantics and the inline test path.
 */
class NotificationRuleEngineTest {

  private NotificationRuleRepository ruleRepository;
  private NotificationRuleFiringRepository firingRepository;
  private NotificationRuleService ruleService;
  private NotificationSink sink;
  private ThreadPoolTaskExecutor executor;
  private NotificationRuleEngine engine;

  @BeforeEach
  void setUp() {
    ruleRepository = mock(NotificationRuleRepository.class);
    firingRepository = mock(NotificationRuleFiringRepository.class);
    ruleService = mock(NotificationRuleService.class);
    sink = mock(NotificationSink.class);
    executor = mock(ThreadPoolTaskExecutor.class);
    engine = new NotificationRuleEngine(ruleRepository, firingRepository, ruleService,
            List.of(sink), executor);
    when(sink.sinkType()).thenReturn("test");
  }

  @Test
  void singleMatchingRuleFiresAction() {
    NotificationRuleEntity rule = rule(1L, "loan-failures", "RunFailed", null, null, "Loan*", null);
    NotificationActionDto action = new NotificationActionDto("test", null, null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(sink.deliver(eq(rule), eq(action), any())).thenReturn(FiringOutcome.success("ok", 1L));

    DomainEvent event = runFailed("run-1", "LoanOrigination");
    TestEventResponse response = engine.testEvent(event);

    assertThat(response.matchedRuleIds()).containsExactly(1L);
    assertThat(response.fireResults()).hasSize(1);
    FireResultDto result = response.fireResults().get(0);
    assertThat(result.ruleId()).isEqualTo(1L);
    assertThat(result.ruleName()).isEqualTo("loan-failures");
    assertThat(result.sink()).isEqualTo("test");
    assertThat(result.outcome()).isEqualTo("success");
    assertThat(result.detail()).isEqualTo("ok");
  }

  @Test
  void nonMatchingEventTypeDoesNotFire() {
    NotificationRuleEntity rule = rule(1L, "failures", "RunFailed", null, null, null, null);
    when(ruleRepository.findMatchingEnabled("RunCompleted")).thenReturn(List.of());

    TestEventResponse response = engine.testEvent(runCompleted("run-2", "LoanOrigination"));

    assertThat(response.matchedRuleIds()).isEmpty();
    assertThat(response.fireResults()).isEmpty();
  }

  @Test
  void wrongAggregateTypeDoesNotMatch() {
    NotificationRuleEntity rule = rule(1L, "definition-only", "RunFailed", "definition", null, null,
            null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));

    TestEventResponse response = engine.testEvent(runFailed("run-3", "LoanOrigination"));

    assertThat(response.matchedRuleIds()).isEmpty();
    assertThat(response.fireResults()).isEmpty();
  }

  @Test
  void wrongDefinitionPatternDoesNotMatch() {
    NotificationRuleEntity rule = rule(1L, "loan-only", "RunFailed", null, null, "Loan*", null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));

    TestEventResponse response = engine.testEvent(runFailed("run-4", "OtherProcess"));

    assertThat(response.matchedRuleIds()).isEmpty();
    assertThat(response.fireResults()).isEmpty();
  }

  @Test
  void aggregateIdGlobAtThresholdMatches() {
    assertAggregateIdMatch("run-1*", "run-1", true);
  }

  @Test
  void aggregateIdGlobJustBelowDoesNotMatch() {
    assertAggregateIdMatch("run-1*", "run-0", false);
  }

  @Test
  void aggregateIdGlobJustAboveMatches() {
    assertAggregateIdMatch("run-1*", "run-10", true);
  }

  private void assertAggregateIdMatch(String pattern, String runId, boolean shouldMatch) {
    NotificationRuleEntity rule = rule(1L, "run-pattern", "RunFailed", null, pattern, null, null);
    NotificationActionDto action = new NotificationActionDto("test", null, null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(sink.deliver(eq(rule), eq(action), any())).thenReturn(FiringOutcome.success("ok", 0L));

    TestEventResponse response = engine.testEvent(runFailed(runId, "LoanOrigination"));

    if (shouldMatch) {
      assertThat(response.matchedRuleIds()).containsExactly(1L);
      assertThat(response.fireResults()).hasSize(1);
    } else {
      assertThat(response.matchedRuleIds()).isEmpty();
      assertThat(response.fireResults()).isEmpty();
    }
  }

  @Test
  void statusFilterMatchesCaseInsensitively() {
    NotificationRuleEntity rule = rule(1L, "failed-only", "RunFailed", null, null, null, "failed");
    NotificationActionDto action = new NotificationActionDto("test", null, null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(sink.deliver(eq(rule), eq(action), any()))
            .thenReturn(FiringOutcome.noop("placeholder", 0L));

    TestEventResponse response = engine.testEvent(runFailed("run-5", "AnyProcess"));

    assertThat(response.matchedRuleIds()).containsExactly(1L);
    assertThat(response.fireResults()).hasSize(1);
  }

  @Test
  void statusFilterMissingOnEventDoesNotMatch() {
    NotificationRuleEntity rule = rule(1L, "started-with-status", "RunStarted", null, null, null,
            "failed");
    when(ruleRepository.findMatchingEnabled("RunStarted")).thenReturn(List.of(rule));

    TestEventResponse response = engine.testEvent(runStarted("run-6", "AnyProcess"));

    assertThat(response.matchedRuleIds()).isEmpty();
    assertThat(response.fireResults()).isEmpty();
  }

  @Test
  void definitionPatternSkippedWhenEventHasNoDefinitionName() {
    NotificationRuleEntity rule = rule(1L, "piece-rule", "PieceNotified", null, null, "Loan*",
            null);
    NotificationActionDto action = new NotificationActionDto("test", null, null, null);
    when(ruleRepository.findMatchingEnabled("PieceNotified")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    when(sink.deliver(eq(rule), eq(action), any()))
            .thenReturn(FiringOutcome.noop("placeholder", 0L));

    DomainEvent event = new DomainEvent.PieceNotified("piece-1", "email", Instant.now(), null);
    TestEventResponse response = engine.testEvent(event);

    assertThat(response.matchedRuleIds()).containsExactly(1L);
    assertThat(response.fireResults()).hasSize(1);
  }

  @Test
  void multipleMatchedRulesFireInRepositoryOrder() {
    NotificationRuleEntity first = rule(1L, "high", "RunFailed", null, null, null, null, 10);
    NotificationRuleEntity second = rule(2L, "low", "RunFailed", null, null, null, null, 5);
    NotificationActionDto action = new NotificationActionDto("test", null, null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(first, second));
    when(ruleService.actionsOf(first)).thenReturn(List.of(action));
    when(ruleService.actionsOf(second)).thenReturn(List.of(action));
    when(sink.deliver(any(), any(), any())).thenReturn(FiringOutcome.success("ok", 0L));

    TestEventResponse response = engine.testEvent(runFailed("run-7", "AnyProcess"));

    assertThat(response.matchedRuleIds()).containsExactly(1L, 2L);
    assertThat(response.fireResults()).hasSize(2);
    assertThat(response.fireResults().get(0).ruleName()).isEqualTo("high");
    assertThat(response.fireResults().get(1).ruleName()).isEqualTo("low");
  }

  @Test
  void emptyRuleSetReturnsEmptyResponse() {
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of());

    TestEventResponse response = engine.testEvent(runFailed("run-8", "AnyProcess"));

    assertThat(response.matchedRuleIds()).isEmpty();
    assertThat(response.fireResults()).isEmpty();
  }

  @Test
  void malformedAggregateIdPatternDoesNotMatch() {
    NotificationRuleEntity rule = rule(1L, "bad-pattern", "RunFailed", null, "[invalid", null,
            null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));

    TestEventResponse response = engine.testEvent(runFailed("run-9", "AnyProcess"));

    assertThat(response.matchedRuleIds()).isEmpty();
    assertThat(response.fireResults()).isEmpty();
  }

  @Test
  void actionsLoadFailureSkipsRuleAndContinues() {
    NotificationRuleEntity badRule = rule(1L, "bad-actions", "RunFailed", null, null, null, null);
    NotificationRuleEntity goodRule = rule(2L, "good", "RunFailed", null, null, null, null);
    NotificationActionDto action = new NotificationActionDto("test", null, null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(badRule, goodRule));
    when(ruleService.actionsOf(badRule)).thenThrow(new RuntimeException("actions json invalid"));
    when(ruleService.actionsOf(goodRule)).thenReturn(List.of(action));
    when(sink.deliver(eq(goodRule), eq(action), any())).thenReturn(FiringOutcome.success("ok", 0L));

    TestEventResponse response = engine.testEvent(runFailed("run-10", "AnyProcess"));

    assertThat(response.matchedRuleIds()).containsExactly(1L, 2L);
    assertThat(response.fireResults()).hasSize(1);
    assertThat(response.fireResults().get(0).ruleName()).isEqualTo("good");
  }

  @Test
  void unknownSinkRecordsSkippedOutcome() {
    NotificationRuleEntity rule = rule(1L, "webhook-rule", "RunFailed", null, null, null, null);
    NotificationActionDto action = new NotificationActionDto("webhook", "http://example.com", null,
            null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));

    engine = new NotificationRuleEngine(ruleRepository, firingRepository, ruleService,
            List.of(), executor);
    TestEventResponse response = engine.testEvent(runFailed("run-11", "AnyProcess"));

    assertThat(response.matchedRuleIds()).containsExactly(1L);
    assertThat(response.fireResults()).hasSize(1);
    FireResultDto result = response.fireResults().get(0);
    assertThat(result.outcome()).isEqualTo("skipped");
    assertThat(result.detail()).contains("unknown sink");
  }

  @Test
  void sinkExceptionReturnsFailureOutcome() {
    NotificationRuleEntity rule = rule(1L, "boom", "RunFailed", null, null, null, null);
    NotificationActionDto action = new NotificationActionDto("test", null, null, null);
    when(ruleRepository.findMatchingEnabled("RunFailed")).thenReturn(List.of(rule));
    when(ruleService.actionsOf(rule)).thenReturn(List.of(action));
    RuntimeException ex = new RuntimeException("sink exploded");
    when(sink.deliver(eq(rule), eq(action), any())).thenThrow(ex);

    TestEventResponse response = engine.testEvent(runFailed("run-12", "AnyProcess"));

    assertThat(response.fireResults()).hasSize(1);
    FireResultDto result = response.fireResults().get(0);
    assertThat(result.outcome()).isEqualTo("failure");
    assertThat(result.detail()).isEqualTo("sink exploded");
  }

  @Test
  void matchesExposesFilterSemanticsDirectly() {
    NotificationRuleEntity rule = rule(1L, "direct", "RunFailed", "run", "run-*", "Loan*",
            "FAILED");
    DomainEvent match = runFailed("run-1", "LoanOrigination");
    assertThat(NotificationRuleEngine.matches(rule, match)).isTrue();

    NotificationRuleEntity wrongAggregate = rule(1L, "direct", "RunFailed", "definition",
            "run-*", "Loan*", "FAILED");
    NotificationRuleEntity wrongDefinition = rule(1L, "direct", "RunFailed", "run",
            "run-*", "Other*", "FAILED");
    NotificationRuleEntity wrongStatus = rule(1L, "direct", "RunFailed", "run",
            "run-*", "Loan*", "CANCELLED");
    assertThat(NotificationRuleEngine.matches(wrongAggregate, match)).isFalse();
    assertThat(NotificationRuleEngine.matches(wrongDefinition, match)).isFalse();
    assertThat(NotificationRuleEngine.matches(wrongStatus, match)).isFalse();
  }

  private static NotificationRuleEntity rule(Long id, String name, String eventType,
          String aggregateType, String aggregateIdPattern, String definitionPattern,
          String status) {
    return rule(id, name, eventType, aggregateType, aggregateIdPattern, definitionPattern, status,
            0);
  }

  private static NotificationRuleEntity rule(Long id, String name, String eventType,
          String aggregateType, String aggregateIdPattern, String definitionPattern,
          String status, int priority) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(id, name, true, eventType, aggregateType,
            aggregateIdPattern, definitionPattern, status, "[]", priority, "default", now, now);
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

  private static DomainEvent.RunStarted runStarted(String runId, String processName) {
    return new DomainEvent.RunStarted(runId, processName, null, Instant.now(), null);
  }
}
