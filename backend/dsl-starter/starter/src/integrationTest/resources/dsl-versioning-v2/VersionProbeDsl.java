import cbs.nova.dsl.*;
import cbs.nova.dslexamples.versionprobe.v1.VersionProbeModels.*;
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
