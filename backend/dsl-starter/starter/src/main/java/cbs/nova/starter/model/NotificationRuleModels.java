package cbs.nova.starter.model;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * DTOs for the T565 notification rules engine API. {@code secret} is write-only: handlers never
 * echo it back (responses carry {@code null}).
 */
public final class NotificationRuleModels {

  private NotificationRuleModels() {
  }

  public record EventFilterDto(
          String eventType,
          @Nullable String aggregateType,
          @Nullable String aggregateIdPattern,
          @Nullable String definitionPattern,
          @Nullable String status) {
  }

  public record NotificationActionDto(
          String sink,
          @Nullable String url,
          @Nullable String secret,
          @Nullable String to) {
  }

  public record NotificationRuleDto(
          @Nullable Long id,
          String name,
          boolean enabled,
          EventFilterDto eventFilter,
          List<NotificationActionDto> actions,
          int priority,
          String rateClass,
          @Nullable Instant createdAt,
          @Nullable Instant updatedAt) {
  }

  public record ToggleEnabledRequest(boolean enabled) {
  }

  public record ChannelDto(
          String type,
          boolean enabled,
          boolean placeholder) {
  }

  public record TestEventRequest(
          String eventType,
          @Nullable String processName,
          @Nullable String status,
          @Nullable String runId) {
  }

  public record FireResultDto(
          long ruleId,
          String ruleName,
          String sink,
          String outcome,
          @Nullable String detail) {
  }

  public record TestEventResponse(
          List<Long> matchedRuleIds,
          List<FireResultDto> fireResults) {
  }
}
