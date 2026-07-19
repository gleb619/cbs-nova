import java.time.Duration;
import java.util.List;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import cbs.nova.dslexamples.NestedCompensationModels.NestedCompensationIn;
import cbs.nova.dslexamples.NestedCompensationModels.NestedCompensationOut;

void main() {
}

List<DslObject> define() {
  var step1 = Dsl.transaction("ncStep1")
      .input(NestedCompensationIn.class).output(String.class)
      .startToCloseTimeout(Duration.ofSeconds(5))
      .execute(ctx -> Result.success("step1-done"))
      .compensation(ctx -> {
        ctx.log("ncStep1 compensated");
        return Result.success("ok");
      })
      .build();
  var step2 = Dsl.transaction("ncStep2")
      .input(NestedCompensationIn.class).output(String.class)
      .startToCloseTimeout(Duration.ofSeconds(5))
      .execute(ctx -> Result.success("step2-done"))
      .compensation(ctx -> {
        ctx.log("ncStep2 compensated");
        return Result.success("ok");
      })
      .build();
  var step3 = Dsl.transaction("ncStep3")
      .input(NestedCompensationIn.class).output(String.class)
      .startToCloseTimeout(Duration.ofSeconds(5))
      .execute(ctx -> Result.failure(new RuntimeException("step3 deliberately failed")))
      .compensation(ctx -> {
        ctx.log("ncStep3 compensated");
        return Result.success("ok");
      })
      .build();
  var process = Dsl.process("NestedCompensation")
      .input(NestedCompensationIn.class).output(NestedCompensationOut.class)
      .execute(ctx -> {
        NestedCompensationIn in = ctx.body();
        ctx.runTransaction("ncStep1", in);
        ctx.runTransaction("ncStep2", in);
        Result<?> r = ctx.runTransaction("ncStep3", in);
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new NestedCompensationOut(in.jobId(), "COMPLETED", List.of()));
      })
      .compensation(ctx -> {
        ctx.log("NestedCompensation compensated");
        return Result.success("ok");
      })
      .build();
  return List.of(step1, step2, step3, process);
}
