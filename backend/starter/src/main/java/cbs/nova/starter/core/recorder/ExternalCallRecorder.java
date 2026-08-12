package cbs.nova.starter.core.recorder;

import cbs.nova.starter.core.listener.DslExecutionListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface ExternalCallRecorder {

  String TYPE_DATABASE = "database";
  String TYPE_HTTP = "http";
  String TYPE_MQ = "mq";
  String TYPE_FILE_SYSTEM = "filesystem";
  String TYPE_EXTERNAL_API = "external_api";
  String TYPE_MICROSERVICE = "microservice";
  String TYPE_ACTIVITY = "activity";
  String TYPE_OTHER = "other";

  void startRun(@NonNull String runId);

  @NonNull
  List<ExternalCall> finishRun(@NonNull String runId);

  void record(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload);

  @Nullable
  Object findMock(@NonNull String type, @NonNull String target, @NonNull String operation);

  void registerListener(@NonNull DslExecutionListener listener);

  @NonNull
  Map<String, Integer> getGlobalCounts();

  void resetGlobalCounts();

  /**
   * Activates a mock resolver for the current run. Default implementation is a no-op for recorders
   * that do not support mocking.
   */
  default void startMocking(@NonNull MockResolver resolver) {
  }

  /**
   * Deactivates the mock resolver for the current run. Default implementation is a no-op.
   */
  default void stopMocking() {
  }
}
