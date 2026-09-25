import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("VersionProbe")
      .version("v2")
      .input(String.class)
      .output(Map.class)
      .execute(ctx -> {
        String payload = (String) ctx.body();
        return Result.success(Map.of("result", "v2:" + payload));
      })
      .buildList();
}
