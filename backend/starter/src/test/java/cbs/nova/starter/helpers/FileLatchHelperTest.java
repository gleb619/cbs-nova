package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helpers.model.FileLatchIn;
import cbs.nova.starter.helpers.model.FileLatchOut;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class FileLatchHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final FileLatchHelper helper = new FileLatchHelper();
  private final Path latchDir = Path.of(System.getProperty("java.io.tmpdir"),
          "cbs-nova-versioning-latch");

  @Test
  void returnsPayloadWhenReleaseFileAlreadyPresent() throws Exception {
    Files.createDirectories(latchDir);
    String runId = "already-released";
    Path release = latchDir.resolve("release-" + runId);
    Files.writeString(release, "go");

    var ctx = contextFactory.of(new FileLatchIn("lock-" + runId, "release-" + runId, "payload"),
            ExecutionMode.PREVIEW);
    Result<FileLatchOut> result = helper.execute(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().payload()).isEqualTo("payload");
    Files.deleteIfExists(release);
  }

  @Test
  void waitsForReleaseFileCreatedByAnotherThread() throws Exception {
    Files.createDirectories(latchDir);
    String runId = "released-later";
    Path lock = latchDir.resolve("lock-" + runId);
    Path release = latchDir.resolve("release-" + runId);
    Files.deleteIfExists(lock);
    Files.deleteIfExists(release);

    var ctx = contextFactory.of(new FileLatchIn("lock-" + runId, "release-" + runId, "payload"),
            ExecutionMode.PREVIEW);

    CompletableFuture<Result<FileLatchOut>> future = CompletableFuture
            .supplyAsync(() -> helper.execute(ctx));

    // wait until lock file appears, then create release file
    int attempts = 0;
    while (!Files.exists(lock) && attempts < 100) {
      Thread.sleep(10);
      attempts++;
    }
    Files.writeString(release, "go");

    Result<FileLatchOut> result = future.get(5, TimeUnit.SECONDS);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().payload()).isEqualTo("payload");
    Files.deleteIfExists(lock);
    Files.deleteIfExists(release);
  }
}
