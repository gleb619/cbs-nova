package cbs.nova.starter.vhs.management;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Service backing the VHS tape management API.
 *
 * <p>
 * Delegates tape discovery to {@link VhsTapeStore} and replay scheduling to an optional
 * {@link VhsReplayJob}. When replay is disabled the replay endpoint surfaces a clear 503 rather
 * than silently failing.
 */
@Slf4j
@AllArgsConstructor
public final class VhsManagementService {

  private final VhsTapeStore store;
  private final ObjectProvider<VhsReplayJob> replayJobProvider;

  @NonNull
  public List<TapeSummary> listTapes() {
    return store.list();
  }

  @NonNull
  public Optional<TapeSummary> findTape(@NonNull String runId) {
    return store.findByRunId(runId);
  }

  @NonNull
  public Optional<InputStream> openTapeStream(@NonNull String runId) {
    return store.openStream(runId);
  }

  public boolean deleteTape(@NonNull String runId) {
    return store.delete(runId);
  }

  @NonNull
  public Optional<ReplayRunResponse> startReplay(@NonNull String runId,
          @NonNull ReplayRunRequest request) {
    VhsReplayJob job = replayJobProvider.getIfAvailable();
    if (job == null) {
      log.warn("VHS replay requested but cbs.vhs.replay.enabled is not true");
      return Optional.empty();
    }
    Optional<Path> tapePath = store.findPath(runId);
    if (tapePath.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(job.start(tapePath.get(), request));
  }
}
