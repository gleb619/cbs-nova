package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.FileLatchIn;
import cbs.nova.starter.helpers.model.FileLatchOut;
import io.temporal.workflow.Workflow;
import org.jspecify.annotations.NonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Helper(name = "fileLatch")
public class FileLatchHelper implements Executable<FileLatchIn, FileLatchOut> {

  private static final Path LATCH_DIR = Path
          .of(System.getProperty("java.io.tmpdir"), "cbs-nova-versioning-latch");

  @Override
  public @NonNull Result<FileLatchOut> execute(@NonNull Context<FileLatchIn> ctx) {
    FileLatchIn in = ctx.body();
    try {
      Files.createDirectories(LATCH_DIR);
      Path lock = LATCH_DIR.resolve(in.lockFileName());
      Path release = LATCH_DIR.resolve(in.releaseFileName());
      Files.writeString(lock, "locked");
      while (!Files.exists(release)) {
        Workflow.sleep(Duration.ofMillis(100));
      }
      Files.deleteIfExists(lock);
      return Result.success(new FileLatchOut(in.payload()));
    } catch (Exception e) {
      return Result.failure(e);
    }
  }
}
