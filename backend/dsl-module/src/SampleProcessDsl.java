import cbs.nova.dsl.*;
import java.util.List;

void main() {}

List<DslObject> define() {
  return Dsl.process("SampleProcess")
      .input(String.class)
      .output(String.class)
      .execute(ctx -> Result.success("Hello from DSL: " + ctx.body()))
      .buildList();
}
