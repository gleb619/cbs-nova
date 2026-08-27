import cbs.nova.dslexamples.ExceptionProbeModels.*;
import cbs.nova.starter.helper.model.ConditionalFailIn;


List<DslObject> define() {
  return Dsl.process("ExceptionProbe")
      .input(ExceptionProbeIn.class)
      .output(ExceptionProbeOut.class)
      .execute(ctx -> {
        ExceptionProbeIn in = ctx.body();
        var r = ctx.runHelper("conditionalFailing",
            new ConditionalFailIn(in.shouldFail(), in.reason()));
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new ExceptionProbeOut("SUCCESS"));
      })
      .compensation(ctx -> {
        ctx.log("ExceptionProbe failed: " + ctx.error().getMessage());
        return Result.success("compensated");
      })
      .buildList();
}
