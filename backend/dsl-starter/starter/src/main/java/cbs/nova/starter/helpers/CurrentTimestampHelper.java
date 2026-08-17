package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helpers.model.CurrentTimestampIn;
import cbs.nova.starter.helpers.model.CurrentTimestampOut;
import io.temporal.workflow.Workflow;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Helper(name = "currentTimestamp")
public class CurrentTimestampHelper implements Executable<CurrentTimestampIn, CurrentTimestampOut> {

  @Override
  public @NonNull Result<CurrentTimestampOut> execute(@NonNull Context<CurrentTimestampIn> ctx) {
    CurrentTimestampIn input = ctx.body();
    ZoneId zone = resolveZone(input);
    Instant now = now();
    String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(zone)
            .format(now);
    return Result.success(new CurrentTimestampOut(timestamp));
  }

  private static ZoneId resolveZone(CurrentTimestampIn input) {
    try {
      return (input.zone() != null && !input.zone().isBlank())
              ? ZoneId.of(input.zone())
              : ZoneId.of("UTC");
    } catch (Exception e) {
      return ZoneId.of("UTC");
    }
  }

  private static Instant now() {
    try {
      return Instant.ofEpochMilli(Workflow.currentTimeMillis());
    } catch (Throwable e) {
      return Instant.now();
    }
  }
}
