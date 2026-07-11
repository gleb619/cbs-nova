import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import cbs.nova.dslmodel.LongWorkIn;
import cbs.nova.dslmodel.LongWorkOut;

void main() {
}

List<DslObject> define() {
  return Dsl.transaction("LongWorkSimulation")
      .input(LongWorkIn.class)
      .output(LongWorkOut.class)
      .startToCloseTimeout(Duration.ofSeconds(30))
      .heartbeatTimeout(Duration.ofSeconds(5))
      .execute(ctx -> {
        LongWorkIn in = (LongWorkIn) ctx.body();
        int completed = 0;
        for (int i = 0; i < in.steps(); i++) {
          completed++;
        }
        return Result.success(new LongWorkOut(in.taskId(), "COMPLETED", completed));
      })
      .buildList();
}