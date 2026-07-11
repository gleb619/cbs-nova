import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import cbs.nova.dslmodel.ExceptionProbeIn;
import cbs.nova.dslmodel.ExceptionProbeOut;
import cbs.nova.starter.helpers.model.ConditionalFailIn;

void main() {
}

List<DslObject> define() {
  return Dsl.process("ExceptionProbe")
      .input(ExceptionProbeIn.class)
      .output(ExceptionProbeOut.class)
      .execute(ctx -> {
        ExceptionProbeIn in = (ExceptionProbeIn) ctx.body();
        Result<?> r = ctx.runHelper("conditionalFailing",
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
