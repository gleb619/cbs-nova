import cbs.nova.dsl.*;
import java.util.List;

List<DslObject> define() {
  return Dsl.process("SimpleOrder")
      .input(String.class)
      .output(String.class)
      .execute(ctx -> {
        return Result.success(
            "Order " + ctx.body() + " confirmed (runId=" + ctx.runId() + ")");
      })
      .compensation(ctx -> {
        ctx.log("compensating SimpleOrder: " + ctx.error().getMessage());
        return Result.success("COMPENSATED");
      })
      .buildList();
}
