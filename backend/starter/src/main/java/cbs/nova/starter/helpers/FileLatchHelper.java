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
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Helper(name = "fileLatch")
public class FileLatchHelper implements Executable<FileLatchIn, FileLatchOut> {

  private static final Path LATCH_DIR = Path
          .of(System.getProperty("java.io.tmpdir"), "cbs-nova-versioning-latch");

  private static final ConcurrentHashMap<Path, Object> MONITORS = new ConcurrentHashMap<>();
  private static final AtomicReference<Thread> WATCHER = new AtomicReference<>();
  private static final long WATCHER_POLL_MILLIS = 50;

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
    Object monitor = MONITORS.computeIfAbsent(release, p -> new Object());
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
      for (Path release : new ArrayList<>(MONITORS.keySet())) {
        if (Files.exists(release)) {
          Object monitor = MONITORS.remove(release);
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
