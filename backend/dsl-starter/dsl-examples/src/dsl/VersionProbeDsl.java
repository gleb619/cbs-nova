import cbs.nova.dslexamples.VersionProbeModels.VersionProbeIn;
import cbs.nova.dslexamples.VersionProbeModels.VersionProbeOut;
import cbs.nova.starter.helpers.model.FileLatchIn;
import cbs.nova.starter.helpers.model.FileLatchOut;


List<DslObject> define() {
  return Dsl.process("VersionProbe")
      .input(VersionProbeIn.class)
      .output(VersionProbeOut.class)
      .version("v1")
      .execute(ctx -> {
        VersionProbeIn in = ctx.body();
        String lockFile = "lock-" + ctx.runId();
        String releaseFile = "release-" + ctx.runId();
        var latch = ctx.runHelper("fileLatch",
            new FileLatchIn(lockFile, releaseFile, in.payload()));
        if (!latch.isSuccess()) {
          return Result.failure(latch.cause());
        }
        FileLatchOut out = latch.as(FileLatchOut.class);
        return Result.success(new VersionProbeOut("v1:" + out.payload()));
      })
      .buildList();
}
