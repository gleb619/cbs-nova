package cbs.nova.starter.model;

import org.jspecify.annotations.Nullable;

public final class ScheduleModels {

  private ScheduleModels() {
  }

  public record CreateScheduleRequest(
          String definition,
          String cron,
          @Nullable String timezone,
          @Nullable Object input,
          @Nullable String note,
          @Nullable String suffix) {

    public CreateScheduleRequest(String definition, String cron, @Nullable String timezone,
            @Nullable Object input, @Nullable String note) {
      this(definition, cron, timezone, input, note, null);
    }
  }

  public record ScheduleActionRequest(
          @Nullable String reason) {
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

  public record UpdateScheduleRequest(
          String cron,
          @Nullable String timezone) {
  }
}
