import cbs.nova.dsl.*;
import cbs.nova.dslexamples.LongWorkModels.*;
import java.time.Duration;
import java.util.List;

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