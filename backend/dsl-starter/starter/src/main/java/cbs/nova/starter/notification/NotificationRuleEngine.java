package cbs.nova.starter.notification;

import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.DomainEventListener;
import cbs.nova.starter.model.NotificationRuleModels.FireResultDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.model.NotificationRuleModels.TestEventResponse;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.persistence.NotificationRuleRepository;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * T565 notification rules engine. Registered as a {@link DomainEventListener}: after the publisher
 * appends a row to {@code dsl_events}, the engine loads the enabled rules for the event type (exact
 * SQL match, priority desc), applies the in-memory filters (aggregate type, aggregate-id glob,
 * definition-name glob, status), and dispatches each action of each matched rule to its
 * {@link NotificationSink} on the bounded {@code cbsNovaNotificationExecutor}. One firing-audit row
 * is written per action. Nothing in this class can break the publish path: rule loading, sink
 * delivery and firing inserts are all individually guarded.
 */
@Slf4j
@RequiredArgsConstructor
public class NotificationRuleEngine implements DomainEventListener {

  private static final int DETAIL_MAX_LENGTH = 1024;

  private final NotificationRuleRepository ruleRepository;
  private final NotificationRuleFiringRepository firingRepository;
  private final NotificationRuleService ruleService;
  private final List<NotificationSink> sinks;
  private final ThreadPoolTaskExecutor notificationExecutor;

  @Override
  public void onEvent(DomainEvent event, long eventRowId) {
    List<NotificationRuleEntity> matched;
    try {
      matched = matchingRules(event);
    } catch (Exception ex) {
      log.warn("[DSL notifications] rule matching failed for {}: {}", event.eventType(),
              ex.getMessage());
      return;
    }
    for (NotificationRuleEntity rule : matched) {
      List<NotificationActionDto> actions;
      try {
        actions = ruleService.actionsOf(rule);
      } catch (Exception ex) {
        log.warn("[DSL notifications] failed to read actions of rule {}: {}", rule.id(),
                ex.getMessage());
        continue;
      }
      for (NotificationActionDto action : actions) {
        notificationExecutor.execute(() -> fireAndRecord(rule, action, event, eventRowId));
      }
    }
  }

  /**
   * Synchronous, side-effect-free(ish) test path used by {@code POST /api/dsl/notifications/test}:
   * runs the same matching + sink path inline (no executor) and returns the results WITHOUT
   * persisting firing rows and without inserting into {@code dsl_events}.
   */
  public TestEventResponse testEvent(DomainEvent event) {
    List<NotificationRuleEntity> matched = matchingRules(event);
    Map<String, NotificationSink> sinksByType = sinksByType();
    List<FireResultDto> results = new ArrayList<>();
    for (NotificationRuleEntity rule : matched) {
      List<NotificationActionDto> actions;
      try {
        actions = ruleService.actionsOf(rule);
      } catch (Exception ex) {
        log.warn("[DSL notifications] failed to read actions of rule {}: {}", rule.id(),
                ex.getMessage());
        continue;
      }
      for (NotificationActionDto action : actions) {
        FiringOutcome outcome = deliver(rule, action, event, sinksByType);
        results.add(new FireResultDto(rule.id() != null ? rule.id() : -1L, rule.name(),
                action.sink(), outcome.outcome(), outcome.detail()));
      }
    }
    List<Long> matchedRuleIds = matched.stream()
            .map(NotificationRuleEntity::id)
            .filter(java.util.Objects::nonNull)
            .toList();
    return new TestEventResponse(matchedRuleIds, results);
  }

  private List<NotificationRuleEntity> matchingRules(DomainEvent event) {
    return ruleRepository.findMatchingEnabled(event.eventType()).stream()
            .filter(rule -> matches(rule, event))
            .toList();
  }

  /** Phase-1 matching, no SpEL: exact event type (in SQL) + null-or-equal/glob dimensions. */
  static boolean matches(NotificationRuleEntity rule, DomainEvent event) {
    if (!rule.eventType().equals(event.eventType())) {
      return false;
    }
    if (rule.aggregateType() != null && !rule.aggregateType().equals(event.aggregateType())) {
      return false;
    }
    if (rule.aggregateIdPattern() != null
            && !globMatches(rule.aggregateIdPattern(), event.aggregateId())) {
      return false;
    }
    if (rule.definitionPattern() != null) {
      String definitionName = definitionNameOf(event);
      // Best-effort extraction: when the event carries no definition name (e.g. PieceNotified)
      // the definition-pattern dimension is skipped rather than failing closed.
      if (definitionName != null && !globMatches(rule.definitionPattern(), definitionName)) {
        return false;
      }
    }
    if (rule.status() != null) {
      String status = statusOf(event);
      // A status filter never matches an event that carries no status.
      if (status == null || !rule.status().equalsIgnoreCase(status)) {
        return false;
      }
    }
    return true;
  }

  private void fireAndRecord(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event, long eventRowId) {
    FiringOutcome outcome = deliver(rule, action, event, sinksByType());
    try {
      firingRepository.insert(new NotificationRuleFiringEntity(
              null,
              eventRowId,
              rule.id() != null ? rule.id() : -1L,
              rule.name(),
              action.sink(),
              outcome.outcome(),
              truncate(outcome.detail()),
              outcome.durationMs(),
              Instant.now()));
    } catch (Exception ex) {
      log.warn("[DSL notifications] failed to record firing of rule {} sink {}: {}", rule.id(),
              action.sink(), ex.getMessage());
    }
  }

  private FiringOutcome deliver(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event, Map<String, NotificationSink> sinksByType) {
    NotificationSink sink = sinksByType.get(action.sink());
    if (sink == null) {
      return FiringOutcome.skipped("unknown sink: " + action.sink());
    }
    try {
      return sink.deliver(rule, action, event);
    } catch (Exception ex) {
      log.warn("[DSL notifications] sink {} failed for rule {}: {}", action.sink(), rule.id(),
              ex.getMessage());
      return FiringOutcome.failure(ex.getMessage(), 0L);
    }
  }

  private Map<String, NotificationSink> sinksByType() {
    return sinks.stream().collect(Collectors.toMap(NotificationSink::sinkType,
            Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
  }

  private static boolean globMatches(String pattern, String value) {
    try {
      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
      return matcher.matches(Path.of(value));
    } catch (Exception ex) {
      return false;
    }
  }

  private static @Nullable String definitionNameOf(DomainEvent event) {
    return switch (event) {
      case DomainEvent.RunStarted e -> e.processName();
      case DomainEvent.RunCompleted e -> e.processName();
      case DomainEvent.RunFailed e -> e.processName();
      case DomainEvent.RunCancelled e -> e.processName();
      case DomainEvent.RunStale e -> e.processName();
      case DomainEvent.DraftSaved e -> e.definitionName();
      case DomainEvent.DraftPublished e -> e.definitionName();
      case DomainEvent.ReloadFailed e -> e.definitionName();
      case DomainEvent.PieceNotified _ -> null;
    };
  }

  private static @Nullable String statusOf(DomainEvent event) {
    return switch (event) {
      case DomainEvent.RunCompleted e -> e.status().name();
      case DomainEvent.RunFailed e -> e.status().name();
      case DomainEvent.RunCancelled e -> e.status().name();
      case DomainEvent.RunStale e -> e.status().name();
      case DomainEvent.RunStarted _ -> null;
      case DomainEvent.DraftSaved _ -> null;
      case DomainEvent.DraftPublished _ -> null;
      case DomainEvent.ReloadFailed _ -> null;
      case DomainEvent.PieceNotified _ -> null;
    };
  }

  private static @Nullable String truncate(@Nullable String value) {
    if (value == null || value.length() <= DETAIL_MAX_LENGTH) {
      return value;
    }
    return value.substring(0, DETAIL_MAX_LENGTH);
  }

}
