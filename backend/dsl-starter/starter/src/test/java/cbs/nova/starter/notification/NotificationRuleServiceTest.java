package cbs.nova.starter.notification;

import static cbs.nova.starter.core.StarterConstants.OUTCOME_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.model.NotificationRuleModels.EventFilterDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationRuleDto;
import cbs.nova.starter.persistence.NotificationRuleRepository;
import cbs.nova.starter.service.DslAuditService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit specs for {@link NotificationRuleService}. Pure Mockito: repository and audit provider are
 * mocked; a real Jackson {@link ObjectMapper} covers JSON (de)serialization.
 */
class NotificationRuleServiceTest {

  private NotificationRuleRepository repository;
  private ObjectMapper objectMapper;
  private ObjectProvider<DslAuditService> auditProvider;
  private DslAuditService auditService;
  private NotificationRuleService service;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationRuleRepository.class);
    objectMapper = new ObjectMapper();
    auditService = mock(DslAuditService.class);
    auditProvider = providerOf(auditService);
    service = new NotificationRuleService(repository, objectMapper, auditProvider);
  }

  @Test
  void createInsertsAndReturnsRuleWithAuditedSuccess() {
    NotificationRuleDto request = ruleDto("failures", true, "RunFailed", List.of(webhookAction()));
    when(repository.insert(any())).thenReturn(42L);

    NotificationRuleEntity created = service.create(request);

    assertThat(created.id()).isEqualTo(42L);
    assertThat(created.name()).isEqualTo("failures");
    assertThat(created.eventType()).isEqualTo("RunFailed");
    assertThat(created.rateClass()).isEqualTo("default");
    assertThat(created.actionsJson())
            .isEqualTo(
                    "[{\"sink\":\"webhook\",\"url\":\"http://hook\",\"secret\":null,\"to\":null}]");
    verify(repository).insert(any());
    verify(auditService).record(eq("anonymous"), eq("notification_rule.create"),
            eq("notification-rule:42"), eq(null), eq(OUTCOME_SUCCESS),
            eq(Map.of("name", "failures")));
  }

  @Test
  void createDefaultsRateClassWhenBlank() {
    NotificationRuleDto request = new NotificationRuleDto(null, "r1", true,
            new EventFilterDto("RunFailed", null, null, null, null),
            List.of(new NotificationActionDto("email", null, null, "ops@x.com")),
            0, "   ", null, null);
    when(repository.insert(any())).thenReturn(1L);

    NotificationRuleEntity created = service.create(request);

    assertThat(created.rateClass()).isEqualTo("default");
  }

  @Test
  void updateReturnsEmptyWhenRuleMissing() {
    NotificationRuleDto request = ruleDto("r", true, "RunFailed", List.of(webhookAction()));
    when(repository.findById(7L)).thenReturn(Optional.empty());

    Optional<NotificationRuleEntity> updated = service.update(7L, request);

    assertThat(updated).isEmpty();
    verify(repository, never()).update(any());
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void updatePersistsRuleAndAudits() {
    Instant created = Instant.parse("2026-01-01T00:00:00Z");
    Instant oldUpdated = created;
    NotificationRuleEntity existing = entity(7L, "old", true, "RunFailed", "[]",
            "default", created, oldUpdated);
    NotificationRuleDto request = new NotificationRuleDto(null, "new", false,
            new EventFilterDto("RunCompleted", null, null, null, null),
            List.of(new NotificationActionDto("slack", "https://slack", null, null)),
            0, "premium", null, null);
    when(repository.findById(7L)).thenReturn(Optional.of(existing));

    Optional<NotificationRuleEntity> result = service.update(7L, request);

    assertThat(result).isPresent();
    NotificationRuleEntity updated = result.get();
    assertThat(updated.name()).isEqualTo("new");
    assertThat(updated.enabled()).isFalse();
    assertThat(updated.eventType()).isEqualTo("RunCompleted");
    assertThat(updated.rateClass()).isEqualTo("premium");
    assertThat(updated.createdAt()).isEqualTo(created);
    assertThat(updated.updatedAt()).isAfterOrEqualTo(created);
    verify(repository).update(updated);
    verify(auditService).record(eq("anonymous"), eq("notification_rule.update"),
            eq("notification-rule:7"), eq(null), eq(OUTCOME_SUCCESS),
            eq(Map.of("name", "new")));
  }

  @Test
  void setEnabledReturnsEmptyWhenRuleMissing() {
    when(repository.findById(9L)).thenReturn(Optional.empty());

    Optional<NotificationRuleEntity> result = service.setEnabled(9L, false);

    assertThat(result).isEmpty();
    verify(repository, never()).update(any());
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void setEnabledTogglesAndAudits() {
    Instant now = Instant.now();
    NotificationRuleEntity existing = entity(9L, "rule", true, "RunFailed",
            "[{\"sink\":\"webhook\",\"url\":\"u\"}]", "default", now, now);
    when(repository.findById(9L)).thenReturn(Optional.of(existing));

    Optional<NotificationRuleEntity> result = service.setEnabled(9L, false);

    assertThat(result).isPresent();
    assertThat(result.get().enabled()).isFalse();
    assertThat(result.get().updatedAt()).isAfterOrEqualTo(now);
    verify(repository).update(result.get());
    verify(auditService).record(eq("anonymous"), eq("notification_rule.toggle"),
            eq("notification-rule:9"), eq(null), eq(OUTCOME_SUCCESS),
            eq(Map.of("name", "rule")));
  }

  @Test
  void deleteReturnsFalseWhenRuleMissing() {
    when(repository.findById(3L)).thenReturn(Optional.empty());

    boolean deleted = service.delete(3L);

    assertThat(deleted).isFalse();
    verify(repository, never()).delete(anyLong());
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void deleteRemovesRuleAndAudits() {
    NotificationRuleEntity existing = entity(3L, "gone", true, "RunFailed", "[]",
            "default", Instant.now(), Instant.now());
    when(repository.findById(3L)).thenReturn(Optional.of(existing));

    boolean deleted = service.delete(3L);

    assertThat(deleted).isTrue();
    verify(repository).delete(3L);
    verify(auditService).record(eq("anonymous"), eq("notification_rule.delete"),
            eq("notification-rule:3"), eq(null), eq(OUTCOME_SUCCESS),
            eq(Map.of("name", "gone")));
  }

  @Test
  void auditFailureIsSwallowed() {
    NotificationRuleDto request = ruleDto("r", true, "RunFailed", List.of(webhookAction()));
    when(auditProvider.getIfAvailable()).thenThrow(new RuntimeException("audit down"));
    when(repository.insert(any())).thenReturn(5L);

    assertThat(service.create(request)).isNotNull();

    verify(repository).insert(any());
  }

  @Test
  void auditRecordFailureIsSwallowed() {
    NotificationRuleDto request = ruleDto("r", true, "RunFailed", List.of(webhookAction()));
    doThrow(new RuntimeException("store down")).when(auditService).record(any(), any(), any(),
            any(), any(), any());
    when(repository.insert(any())).thenReturn(5L);

    assertThat(service.create(request)).isNotNull();

    verify(repository).insert(any());
  }

  @Test
  void validateRejectsBlankName() {
    NotificationRuleDto request = ruleDto("   ", true, "RunFailed", List.of(webhookAction()));

    assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'name' is required and must not be blank");
  }

  @Test
  void validateRejectsMissingEventType() {
    NotificationRuleDto request = new NotificationRuleDto(null, "r", true,
            new EventFilterDto(null, null, null, null, null),
            List.of(webhookAction()), 0, "default", null, null);

    assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'eventFilter.eventType' is required and must not be blank");
  }

  @Test
  void validateRejectsEmptyActions() {
    NotificationRuleDto request = ruleDto("r", true, "RunFailed", List.of());

    assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'actions' is required and must not be empty");
  }

  @Test
  void validateRejectsBlankSink() {
    NotificationRuleDto request = ruleDto("r", true, "RunFailed",
            List.of(new NotificationActionDto("  ", null, null, null)));

    assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("every action requires a non-blank 'sink'");
  }

  @Test
  void validateRejectsUrlRequiredSinksWithoutUrl() {
    for (String sink : List.of("webhook", "slack", "pagerduty")) {
      NotificationRuleDto request = ruleDto("r", true, "RunFailed",
              List.of(new NotificationActionDto(sink, null, null, null)));

      assertThatThrownBy(() -> service.create(request))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("sink '" + sink + "' requires a non-blank 'url'");
    }
  }

  @Test
  void actionsOfParsesJsonActions() {
    NotificationRuleEntity entity = entity(1L, "r", true, "RunFailed",
            "[{\"sink\":\"webhook\",\"url\":\"http://h\",\"secret\":\"s\"}]",
            "default", Instant.now(), Instant.now());

    List<NotificationActionDto> actions = service.actionsOf(entity);

    assertThat(actions).hasSize(1);
    assertThat(actions.get(0).sink()).isEqualTo("webhook");
    assertThat(actions.get(0).url()).isEqualTo("http://h");
    assertThat(actions.get(0).secret()).isEqualTo("s");
  }

  @Test
  void actionsOfThrowsIllegalStateWhenJsonInvalid() {
    NotificationRuleEntity entity = entity(1L, "r", true, "RunFailed", "not-json",
            "default", Instant.now(), Instant.now());

    assertThatThrownBy(() -> service.actionsOf(entity))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to parse actions JSON of notification rule 1");
  }

  @Test
  void filterOfMapsEntityColumns() {
    NotificationRuleEntity entity = entity(1L, "r", true, "RunFailed", "[]",
            "default", Instant.now(), Instant.now());

    EventFilterDto filter = service.filterOf(entity);

    assertThat(filter.eventType()).isEqualTo("RunFailed");
    assertThat(filter.aggregateType()).isNull();
  }

  private static NotificationRuleDto ruleDto(String name, boolean enabled, String eventType,
          List<NotificationActionDto> actions) {
    return new NotificationRuleDto(null, name, enabled,
            new EventFilterDto(eventType, null, null, null, null),
            actions, 0, "default", null, null);
  }

  private static NotificationActionDto webhookAction() {
    return new NotificationActionDto("webhook", "http://hook", null, null);
  }

  private static NotificationRuleEntity entity(long id, String name, boolean enabled,
          String eventType, String actionsJson, String rateClass, Instant createdAt,
          Instant updatedAt) {
    return new NotificationRuleEntity(id, name, enabled, eventType, null, null, null, null,
            actionsJson, 0, rateClass, createdAt, updatedAt);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<DslAuditService> providerOf(DslAuditService service) {
    ObjectProvider<DslAuditService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(service);
    return provider;
  }
}
