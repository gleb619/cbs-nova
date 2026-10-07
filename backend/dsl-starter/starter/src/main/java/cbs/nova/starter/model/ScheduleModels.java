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
          @Nullable String suffix,
          @Nullable String executionTimeout) {

    public CreateScheduleRequest(String definition, String cron, @Nullable String timezone,
            @Nullable Object input, @Nullable String note) {
      this(definition, cron, timezone, input, note, null, null);
    }

    public CreateScheduleRequest(String definition, String cron, @Nullable String timezone,
            @Nullable Object input, @Nullable String note, @Nullable String suffix) {
      this(definition, cron, timezone, input, note, suffix, null);
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
          boolean paused,
          @Nullable String executionTimeout) {
  }

  public record CreateScheduleResponse(
          String scheduleId,
          String definition,
          String cron) {
  }

  public record UpdateScheduleRequest(
          String cron,
          @Nullable String timezone,
          @Nullable String executionTimeout) {

    public UpdateScheduleRequest(String cron) {
      this(cron, null, null);
    }

    public UpdateScheduleRequest(String cron, @Nullable String timezone) {
      this(cron, timezone, null);
    }
  }
}
