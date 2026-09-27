package cbs.nova.starter.vhs.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit specs for {@link VhsManagementService}.
 *
 * <p>
 * Tape discovery operations are delegated straight to {@link VhsTapeStore}. Replay scheduling
 * depends on an optional {@link VhsReplayJob}; when replay is disabled the service returns
 * {@link Optional#empty()} rather than throwing.
 */
class VhsManagementServiceTest {

  private static final String RUN_ID = "run-123";
  private static final Instant RECORDED_AT = Instant.parse("2026-09-27T10:00:00Z");

  private final VhsTapeStore store = mock(VhsTapeStore.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<VhsReplayJob> replayJobProvider = mock(ObjectProvider.class);
  private final VhsManagementService service = new VhsManagementService(store, replayJobProvider);

  @Test
  void listTapesDelegatesToStore() {
    TapeSummary summary = summary(RUN_ID);
    when(store.list()).thenReturn(List.of(summary));

    List<TapeSummary> result = service.listTapes();

    assertThat(result).containsExactly(summary);
    verify(store).list();
  }

  @Test
  void findTapeDelegatesToStore() {
    TapeSummary summary = summary(RUN_ID);
    when(store.findByRunId(RUN_ID)).thenReturn(Optional.of(summary));

    Optional<TapeSummary> result = service.findTape(RUN_ID);

    assertThat(result).hasValue(summary);
    verify(store).findByRunId(RUN_ID);
  }

  @Test
  void openTapeStreamDelegatesToStore() {
    InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
    when(store.openStream(RUN_ID)).thenReturn(Optional.of(stream));

    Optional<InputStream> result = service.openTapeStream(RUN_ID);

    assertThat(result).hasValue(stream);
    verify(store).openStream(RUN_ID);
  }

  @Test
  void deleteTapeDelegatesToStore() {
    when(store.delete(RUN_ID)).thenReturn(true);

    boolean result = service.deleteTape(RUN_ID);

    assertThat(result).isTrue();
    verify(store).delete(RUN_ID);
  }

  @Test
  void startReplayReturnsEmptyWhenJobUnavailable() {
    when(replayJobProvider.getIfAvailable()).thenReturn(null);

    Optional<ReplayRunResponse> result = service.startReplay(RUN_ID, request());

    assertThat(result).isEmpty();
    verify(store, never()).findPath(any());
  }

  @Test
  void startReplayReturnsEmptyWhenTapeMissing() {
    VhsReplayJob job = mock(VhsReplayJob.class);
    when(replayJobProvider.getIfAvailable()).thenReturn(job);
    when(store.findPath(RUN_ID)).thenReturn(Optional.empty());

    Optional<ReplayRunResponse> result = service.startReplay(RUN_ID, request());

    assertThat(result).isEmpty();
    verify(store).findPath(RUN_ID);
    verify(job, never()).start(any(), any());
  }

  @Test
  void startReplayDelegatesToJobWhenTapeFound() {
    VhsReplayJob job = mock(VhsReplayJob.class);
    Path path = Path.of("/tapes", RUN_ID + ".vhs.jsonl");
    ReplayRunRequest request = request();
    ReplayRunResponse response = new ReplayRunResponse(
            "replay-abc", "accepted", "dry-run", "exact",
            "/api/v1/vhs/tapes/" + RUN_ID + "/replay/replay-abc", null);
    when(replayJobProvider.getIfAvailable()).thenReturn(job);
    when(store.findPath(RUN_ID)).thenReturn(Optional.of(path));
    when(job.start(eq(path), eq(request))).thenReturn(response);

    Optional<ReplayRunResponse> result = service.startReplay(RUN_ID, request);

    assertThat(result).hasValue(response);
    verify(store).findPath(RUN_ID);
    verify(job).start(path, request);
  }

  private static TapeSummary summary(String runId) {
    return new TapeSummary(
            runId + ".vhs.jsonl", RECORDED_AT, runId, "corr-" + runId, "Ping", 1024L, 4L);
  }

  private static ReplayRunRequest request() {
    return new ReplayRunRequest("dry-run", "exact", 1.0, 1, 1);
  }
}
