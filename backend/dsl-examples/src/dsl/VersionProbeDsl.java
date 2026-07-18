import cbs.nova.dsl.*;
import cbs.nova.dslexamples.VersionProbeModels.*;
import cbs.nova.starter.helpers.model.FileLatchIn;
import cbs.nova.starter.helpers.model.FileLatchOut;
import java.util.List;

void main() {
}

List<DslObject> define() {
  return Dsl.process("VersionProbe")
      .input(VersionProbeIn.class)
      .output(VersionProbeOut.class)
      .version("v1")
      .execute(ctx -> {
        VersionProbeIn in = (VersionProbeIn) ctx.body();
        String lockFile = "lock-" + ctx.runId();
        String releaseFile = "release-" + ctx.runId();
        Result<?> latch = ctx.runHelper("fileLatch",
            new FileLatchIn(lockFile, releaseFile, in.payload()));
        if (!latch.isSuccess()) {
          return Result.failure(latch.cause());
        }
        FileLatchOut out = latch.as(FileLatchOut.class);
        return Result.success(new VersionProbeOut("v1:" + out.payload()));
      })
      .buildList();
}
