package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.FileLatchIn;
import cbs.nova.starter.helper.model.FileLatchOut;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.temporal.workflow.Workflow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;

@Helper(name = "fileLatch")
public class FileLatchHelper implements Executable<FileLatchIn, FileLatchOut> {

  private static final Path LATCH_DIR = Path
          .of(System.getProperty("java.io.tmpdir"), "cbs-nova-versioning-latch");

  // Tunable via system properties because the helper exposes a static API; per-cache
  // configuration through CbsNovaCacheProperties is reserved for Spring-managed instances.
  private static final long WATCHER_POLL_MILLIS = 50L;
  private static final Duration MONITORS_TTL = Duration.ofMinutes(10);
  private static final long MONITORS_MAX_SIZE = 10_000L;

  private static final Cache<Path, Object> MONITORS = Caffeine.newBuilder()
          .expireAfterAccess(MONITORS_TTL)
          .maximumSize(MONITORS_MAX_SIZE)
          .build();
  private static final AtomicReference<Thread> WATCHER = new AtomicReference<>();

  @Override
  public @NonNull Result<FileLatchOut> execute(@NonNull Context<FileLatchIn> ctx) {
    FileLatchIn in = ctx.body();
    try {
      Files.createDirectories(LATCH_DIR);
      Path lock = LATCH_DIR.resolve(in.lockFileName());
      Path release = LATCH_DIR.resolve(in.releaseFileName());
      Files.writeString(lock, "locked");
      while (!Files.exists(release)) {
        if (isWorkflowThread()) {
          Workflow.sleep(Duration.ofMillis(100));
        } else {
          waitForRelease(release);
        }
      }
      Files.deleteIfExists(lock);
      return Result.success(new FileLatchOut(in.payload()));
    } catch (Exception e) {
      return Result.failure(e);
    }
  }

  private static boolean isWorkflowThread() {
    try {
      Workflow.currentTimeMillis();
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  private static void waitForRelease(Path release) throws InterruptedException {
    Object monitor = MONITORS.get(release, k -> new Object());
    ensureWatcher();
    synchronized (monitor) {
      if (!Files.exists(release)) {
        monitor.wait(WATCHER_POLL_MILLIS);
      }
    }
  }

  private static void ensureWatcher() {
    WATCHER.updateAndGet(existing -> {
      if (existing != null && existing.isAlive()) {
        return existing;
      }
      Thread thread = new Thread(FileLatchHelper::watchReleases, "file-latch-watcher");
      thread.setDaemon(true);
      thread.start();
      return thread;
    });
  }

  private static void watchReleases() {
    while (!Thread.currentThread().isInterrupted()) {
      for (Path release : MONITORS.asMap().keySet()) {
        if (Files.exists(release)) {
          Object monitor = MONITORS.getIfPresent(release);
          MONITORS.invalidate(release);
          if (monitor != null) {
            synchronized (monitor) {
              monitor.notifyAll();
            }
          }
        }
      }
      try {
        Thread.sleep(WATCHER_POLL_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
