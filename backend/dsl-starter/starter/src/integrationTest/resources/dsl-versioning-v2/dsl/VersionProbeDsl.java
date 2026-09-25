import VersionProbeModels.VersionProbeIn;
import VersionProbeModels.VersionProbeOut;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslCompactSource;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import java.util.List;

List<DslObject> define() {
  return Dsl.process("VersionProbe")
      .input(VersionProbeIn.class)
      .output(VersionProbeOut.class)
      .version("v2")
      .execute(ctx -> {
        VersionProbeIn in = ctx.body();
        return Result.success(new VersionProbeOut("v2:" + in.payload()));
      })
      .buildList();
}
