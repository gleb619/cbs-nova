import cbs.nova.dslexamples.ScheduleWindowModels.*;
import cbs.nova.starter.helper.model.ParseDurationIn;
import cbs.nova.starter.helper.model.ParseDurationOut;


List<DslObject> define() {
  return Dsl.process("ScheduleWindow")
      .input(ScheduleWindowIn.class)
      .output(ScheduleWindowOut.class)
      .description("Resolves a human-entered schedule window (grace period + hard limit) into normalized milliseconds, seconds, and ISO-8601 form using the parseDuration helper. Both accepted input shapes are demonstrated: an ISO-8601 duration string for the grace period and a shorthand '<number><unit>' string for the hard limit.")
      .execute(ctx -> {
        ScheduleWindowIn in = ctx.body();

        // ISO-8601 form — e.g. "PT1H30M" or "P2DT3H".
        var graceVar = ctx.runHelper("parseDuration",
            new ParseDurationIn(in.gracePeriod()));
        if (!graceVar.isSuccess()) {
          return Result.failure(graceVar.cause());
        }
        ParseDurationOut graceOut = graceVar.as(ParseDurationOut.class);

        // Shorthand form — e.g. "1h30m", "2d12h", "250ms".
        var hardVar = ctx.runHelper("parseDuration",
            new ParseDurationIn(in.hardLimit()));
        if (!hardVar.isSuccess()) {
          return Result.failure(hardVar.cause());
        }
        ParseDurationOut hardOut = hardVar.as(ParseDurationOut.class);

        return Result.success(new ScheduleWindowOut(
            in.jobName(),
            graceOut.millis(),
            graceOut.seconds(),
            graceOut.iso(),
            hardOut.millis(),
            hardOut.seconds(),
            hardOut.iso()));
      })
      .buildList();
}
