package cbs.nova.starter.notification;

import static cbs.nova.starter.core.StarterConstants.OUTCOME_SUCCESS;

import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.model.NotificationRuleModels.EventFilterDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationRuleDto;
import cbs.nova.starter.persistence.NotificationRuleRepository;
import cbs.nova.starter.persistence.NotificationRuleSearchResult;
import cbs.nova.starter.service.DslAuditService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * CRUD + JSON (de)serialization for notification rules. Validation rejects blank names/event types
 * and url-less webhook/slack/pagerduty actions with {@link IllegalArgumentException}; every
 * mutation is audited through {@link DslAuditService} (fail-safe by design).
 */
@Slf4j
@RequiredArgsConstructor
public class NotificationRuleService {

  static final String DEFAULT_RATE_CLASS = "default";

  private static final Set<String> URL_SINKS = Set.of("webhook", "slack", "pagerduty");

  private final NotificationRuleRepository repository;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<DslAuditService> auditService;

  public NotificationRuleSearchResult list(int offset, int limit) {
    return repository.findAll(offset, limit);
  }

  public Optional<NotificationRuleEntity> findById(long id) {
    return repository.findById(id);
  }

  public NotificationRuleEntity create(NotificationRuleDto request) {
    validate(request);
    Instant now = Instant.now();
    long id = repository.insert(toEntity(null, request, now, now));
    audit("notification_rule.create", id, request.name());
    return toEntity(id, request, now, now);
  }

  public Optional<NotificationRuleEntity> update(long id, NotificationRuleDto request) {
    validate(request);
    Optional<NotificationRuleEntity> existing = repository.findById(id);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    NotificationRuleEntity updated = toEntity(id, request,
            existing.get().createdAt(), Instant.now());
    repository.update(updated);
    audit("notification_rule.update", id, request.name());
    return Optional.of(updated);
  }

  public Optional<NotificationRuleEntity> setEnabled(long id, boolean enabled) {
    Optional<NotificationRuleEntity> existing = repository.findById(id);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    NotificationRuleEntity current = existing.get();
    NotificationRuleEntity updated = new NotificationRuleEntity(
            current.id(), current.name(), enabled, current.eventType(), current.aggregateType(),
            current.aggregateIdPattern(), current.definitionPattern(), current.status(),
            current.actionsJson(), current.priority(), current.rateClass(), current.createdAt(),
            Instant.now());
    repository.update(updated);
    audit("notification_rule.toggle", id, current.name());
    return Optional.of(updated);
  }

  public boolean delete(long id) {
    Optional<NotificationRuleEntity> existing = repository.findById(id);
    if (existing.isEmpty()) {
      return false;
    }
    repository.delete(id);
    audit("notification_rule.delete", id, existing.get().name());
    return true;
  }

  public EventFilterDto filterOf(NotificationRuleEntity entity) {
    return new EventFilterDto(entity.eventType(), entity.aggregateType(),
            entity.aggregateIdPattern(), entity.definitionPattern(), entity.status());
  }

  public List<NotificationActionDto> actionsOf(NotificationRuleEntity entity) {
    try {
      return objectMapper.readValue(entity.actionsJson(),
              new TypeReference<List<NotificationActionDto>>() {
              });
    } catch (Exception ex) {
      throw new IllegalStateException(
              "Failed to parse actions JSON of notification rule " + entity.id(), ex);
    }
  }

  private NotificationRuleEntity toEntity(@Nullable Long id, NotificationRuleDto request,
          Instant createdAt, Instant updatedAt) {
    return new NotificationRuleEntity(
            id,
            request.name(),
            request.enabled(),
            request.eventFilter().eventType(),
            request.eventFilter().aggregateType(),
            request.eventFilter().aggregateIdPattern(),
            request.eventFilter().definitionPattern(),
            request.eventFilter().status(),
            writeActions(request.actions()),
            request.priority(),
            request.rateClass() == null || request.rateClass().isBlank()
                    ? DEFAULT_RATE_CLASS
                    : request.rateClass(),
            createdAt,
            updatedAt);
  }

  private String writeActions(List<NotificationActionDto> actions) {
    try {
      return objectMapper.writeValueAsString(actions);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize notification rule actions", ex);
    }
  }

  private static void validate(NotificationRuleDto request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("'name' is required and must not be blank");
    }
    if (request.eventFilter() == null
            || request.eventFilter().eventType() == null
            || request.eventFilter().eventType().isBlank()) {
      throw new IllegalArgumentException(
              "'eventFilter.eventType' is required and must not be blank");
    }
    if (request.actions() == null || request.actions().isEmpty()) {
      throw new IllegalArgumentException("'actions' is required and must not be empty");
    }
    for (NotificationActionDto action : request.actions()) {
      if (action.sink() == null || action.sink().isBlank()) {
        throw new IllegalArgumentException("every action requires a non-blank 'sink'");
      }
      if (URL_SINKS.contains(action.sink())
              && (action.url() == null || action.url().isBlank())) {
        throw new IllegalArgumentException(
                "sink '" + action.sink() + "' requires a non-blank 'url'");
      }
    }
  }

  private void audit(String action, long id, String name) {
    try {
      DslAuditService audit = auditService.getIfAvailable();
      if (audit != null) {
        audit.record(DslAuditService.currentActor(), action, "notification-rule:" + id, null,
                OUTCOME_SUCCESS, Map.of("name", name));
      }
    } catch (Exception ex) {
      log.warn("[DSL notifications] failed to audit {} on rule {}: {}", action, id,
              ex.getMessage());
    }
  }
}
