package cbs.nova.dsl.codegen.task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class VirtualThreads {

  private VirtualThreads() {
  }

  public static <T> List<T> runAll(List<? extends Callable<T>> callables) throws IOException {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = executor.invokeAll(callables);
      var results = new ArrayList<T>(futures.size());
      for (var future : futures) {
        results.add(unwrap(future));
      }
      return results;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while running parallel tasks", e);
    }
  }

  private static <T> T unwrap(Future<T> future) throws IOException {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while running parallel tasks", e);
    } catch (ExecutionException e) {
      var cause = e.getCause();
      if (cause instanceof IOException ioException) {
        throw ioException;
      }
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IOException("Parallel task failed", cause);
    }
  }
}
