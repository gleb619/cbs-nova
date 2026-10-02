import cbs.nova.dslexamples.ScheduleGuardModels.*;
import cbs.nova.starter.helper.model.CronParseIn;
import cbs.nova.starter.helper.model.CronParseOut;
import java.util.List;


List<DslObject> define() {
  return Dsl.process("ScheduleGuard")
      .input(ScheduleGuardIn.class)
      .output(ScheduleGuardOut.class)
      .description("Pre-flight guard for a Temporal schedule: runs the cronParse helper in"
          + " 'validate' mode so an operator-supplied expression fails fast on bad syntax, then"
          + " runs 'nextFire' against a caller-pinned 'after' timestamp to preview the next N"
          + " fire times. Use as a gate before creating a Temporal schedule so misconfigured"
          + " expressions never reach the scheduler.")
      .execute(ctx -> {
        ScheduleGuardIn in = ctx.body();

        // 1) validate mode: fail fast on bad syntax (out-of-range field, unknown macro, etc.).
        var validate = ctx.runHelper("cronParse",
            new CronParseIn(in.expression(), "validate", null, null));
        if (!validate.isSuccess()) {
          // Surface a structured failure so the orchestrator can route the operator
          // message back without re-parsing the cause string.
          return Result.success(new ScheduleGuardOut(
              in.scheduleName(),
              in.expression(),
              false,
              null,
              validate.cause().getMessage()));
        }
        CronParseOut validateOut = validate.as(CronParseOut.class);

        // 2) nextFire mode: preview the next N fire times after a caller-pinned instant.
        //    'fixedAfter' removes wall-clock dependence so the preview is deterministic.
        var preview = ctx.runHelper("cronParse",
            new CronParseIn(in.expression(), "nextFire", in.fixedAfter(), in.previewCount()));
        if (!preview.isSuccess()) {
          return Result.success(new ScheduleGuardOut(
              in.scheduleName(),
              in.expression(),
              false,
              null,
              preview.cause().getMessage()));
        }
        List<String> fireTimes = preview.as(CronParseOut.class).timestamps();

        // Both calls succeeded; validateOut.valid is always true here, but echo the
        // helper flag so callers can audit the validator verdict independently.
        return Result.success(new ScheduleGuardOut(
            in.scheduleName(),
            in.expression(),
            validateOut.valid(),
            fireTimes,
            null));
      })
      .compensation((ctx, history) ->
          ctx.log("ScheduleGuard compensated: " + ctx.error().getMessage()))
      .buildList();
}