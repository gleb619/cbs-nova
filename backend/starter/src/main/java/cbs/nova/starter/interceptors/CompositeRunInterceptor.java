package cbs.nova.starter.interceptors;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composite {@link RunInterceptor} that forwards lifecycle callbacks to all registered
 * interceptors. {@link #afterRun(String)} is invoked in reverse order so that resources started
 * first are cleaned up last.
 */
public final class CompositeRunInterceptor implements RunInterceptor {

  private final List<RunInterceptor> interceptors;

  public CompositeRunInterceptor(@NonNull List<RunInterceptor> interceptors) {
    this.interceptors = List.copyOf(interceptors);
  }

  @Override
  public void beforeRun(@NonNull String runId) {
    for (RunInterceptor interceptor : interceptors) {
      interceptor.beforeRun(runId);
    }
  }

  @Override
  public void afterRun(@NonNull String runId) {
    List<RunInterceptor> reversed = new ArrayList<>(interceptors);
    Collections.reverse(reversed);
    for (RunInterceptor interceptor : reversed) {
      interceptor.afterRun(runId);
    }
  }

  @Override
  public @NonNull AutoCloseable startRun(@NonNull String runId) {
    List<AutoCloseable> handles = new ArrayList<>(interceptors.size());
    try {
      for (RunInterceptor interceptor : interceptors) {
        handles.add(interceptor.startRun(runId));
      }
    } catch (RuntimeException e) {
      closeHandles(handles);
      throw e;
    }
    List<AutoCloseable> reversed = new ArrayList<>(handles);
    Collections.reverse(reversed);
    return () -> {
      Exception firstFailure = null;
      for (AutoCloseable handle : reversed) {
        try {
          handle.close();
        } catch (Exception e) {
          if (firstFailure == null) {
            firstFailure = e;
          }
        }
      }
      if (firstFailure != null) {
        throw firstFailure;
      }
    };
  }

  private void closeHandles(@NonNull List<AutoCloseable> handles) {
    RuntimeException firstFailure = null;
    for (int i = handles.size() - 1; i >= 0; i--) {
      try {
        handles.get(i).close();
      } catch (Exception e) {
        if (firstFailure == null) {
          firstFailure = e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
      }
    }
    if (firstFailure != null) {
      throw firstFailure;
    }
  }
}
