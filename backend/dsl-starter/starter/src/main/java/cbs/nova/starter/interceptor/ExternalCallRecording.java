package cbs.nova.starter.interceptor;

import cbs.nova.starter.core.recorder.ExternalCall;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Provides access to external-call details recorded during the current run. Implemented by
 * interceptors that capture calls from {@link cbs.nova.starter.core.recorder.ExternalCallRecorder}
 * or any other external-call listener system.
 */
@Deprecated(forRemoval = true)
public interface ExternalCallRecording {

  @NonNull
  @Deprecated(forRemoval = true)
  List<ExternalCall> recordedCalls();
}
