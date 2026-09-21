import cbs.nova.dslexamples.AuditTrailModels.*;
import cbs.nova.starter.helper.model.CurrentTimestampIn;
import cbs.nova.starter.helper.model.CurrentTimestampOut;


List<DslObject> define() {
  return Dsl.process("AuditTrail")
      .input(AuditTrailIn.class)
      .output(AuditTrailOut.class)
      .description("Stamps an audit/event record with a Temporal-workflow-safe ISO-8601 timestamp using the currentTimestamp helper. Both forms are demonstrated: the default UTC timestamp (no zone argument) and an explicit timezone via the 'zone' argument, so a downstream audit sink can store a canonical UTC value while retaining a human-readable local offset.")
      .execute(ctx -> {
        AuditTrailIn in = ctx.body();

        // Default UTC form — no zone argument.
        var utcVar = ctx.runHelper("currentTimestamp",
            new CurrentTimestampIn(null));
        if (!utcVar.isSuccess()) {
          return Result.failure(utcVar.cause());
        }
        String utcTimestamp = utcVar.as(CurrentTimestampOut.class).timestamp();

        // Explicit caller-specified timezone — e.g. "Asia/Kolkata".
        var localVar = ctx.runHelper("currentTimestamp",
            new CurrentTimestampIn(in.zone()));
        if (!localVar.isSuccess()) {
          return Result.failure(localVar.cause());
        }
        String localTimestamp = localVar.as(CurrentTimestampOut.class).timestamp();

        return Result.success(new AuditTrailOut(
            in.eventType(),
            in.actor(),
            utcTimestamp,
            localTimestamp,
            in.zone()));
      })
      .buildList();
}