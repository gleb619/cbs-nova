package cbs.nova.starter.model;

import org.jspecify.annotations.Nullable;

/**
 * Request/response records for Temporal Schedule CRUD on published DSL definitions.
 */
public final class ScheduleModels {

  private ScheduleModels() {
  }

  public record CreateScheduleRequest(
          String definition,
          String cron,
          @Nullable String timezone,
          @Nullable Object input,
          @Nullable String note) {
  }

  public record ScheduleSummary(
          String scheduleId,
          String definition,
          String cron,
          String timezone,
          @Nullable String note,
          @Nullable String nextRunAt,
          boolean paused) {
  }

  public record CreateScheduleResponse(
          String scheduleId,
          String definition,
          String cron) {
  }
}
