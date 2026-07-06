package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.CurrentTimestampIn;
import cbs.nova.starter.helpers.model.CurrentTimestampOut;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Helper(name = "currentTimestamp")
public class CurrentTimestampHelper implements Executable<CurrentTimestampIn, CurrentTimestampOut> {

  @Override
  public @NonNull Result<CurrentTimestampOut> execute(@NonNull Context<CurrentTimestampIn> ctx) {
    CurrentTimestampIn input = ctx.body();
    ZoneId zone;
    try {
      zone = (input.zone() != null && !input.zone().isBlank())
              ? ZoneId.of(input.zone())
              : ZoneId.of("UTC");
    } catch (Exception e) {
      zone = ZoneId.of("UTC");
    }
    String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(zone)
            .format(Instant.now());
    return Result.success(new CurrentTimestampOut(timestamp));
  }
}
