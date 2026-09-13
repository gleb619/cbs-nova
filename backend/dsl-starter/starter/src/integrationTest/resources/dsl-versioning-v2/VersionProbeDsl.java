import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslCompactSource;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import cbs.nova.dslexamples.v1.VersionProbeModels.VersionProbeIn;
import cbs.nova.dslexamples.v1.VersionProbeModels.VersionProbeOut;
import java.util.List;

//TODO: instead of use a compiler from source files, add a testcontainer for a `dsl-builder` module

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
