package cbs.nova.starter.vhs.management;

import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import cbs.nova.starter.vhs.replay.ReplayMode;
import cbs.nova.starter.vhs.replay.VhsCallDrivers;
import cbs.nova.starter.vhs.replay.VhsReplayEngine;
import cbs.nova.starter.vhs.replay.VhsReplayReport;
import cbs.nova.starter.vhs.replay.VhsTapeReader;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs one-shot VHS replay jobs.
 *
 * <p>
 * Dry-run/exact replays are executed inline and return a full report. Load-mode replays (and any
 * non-dry-run target) are submitted to a {@link ThreadPoolTaskExecutor} and tracked in memory. The
 * status map is bounded by {@link CbsVhsReplayProperties#statusLimit()} (default
 * {@value CbsVhsReplayProperties#DEFAULT_STATUS_LIMIT}): in-flight (accepted/running) entries are
 * never evicted before they finish, finished entries are evicted oldest-first, and a transient
 * overshoot is allowed when every slot is in-flight. Persistence of job history is a follow-up
 * task.
 */
@Slf4j
@RequiredArgsConstructor
public final class VhsReplayJob {

  private final @NonNull CbsVhsReplayProperties baseProperties;
  private final @NonNull ObjectMapper objectMapper;
  private final @Nullable AsyncTaskExecutor executor;
  private final Map<String, JobStatus> statuses = new LinkedHashMap<>();

  /**
   * Start a replay for the given tape file.
   *
   * @return a response with the initial job status; inline dry-run jobs include the full report
   */
  @NonNull
  public ReplayRunResponse start(@NonNull Path tapeFile, @NonNull ReplayRunRequest request) {
    CbsVhsReplayProperties properties = resolveProperties(request);
    String replayRunId = "replay-" + UUID.randomUUID().toString().substring(0, 8);
    String statusUrl = "/api/v1/vhs/tapes/"
            + tapeFile.getFileName().toString().replace(".vhs.jsonl", "")
            + "/replay/" + replayRunId;

    if (isInline(properties)) {
      VhsReplayReport report = runEngine(properties, tapeFile);
      recordStatus(new JobStatus(replayRunId, "completed", report));
      return new ReplayRunResponse(replayRunId, "completed", properties.target(),
              properties.mode().name(), statusUrl, report);
    }

    recordStatus(new JobStatus(replayRunId, "accepted", null));
    executor.execute(() -> {
      try {
        VhsReplayReport report = runEngine(properties, tapeFile);
        recordStatus(new JobStatus(replayRunId, "completed", report));
      } catch (Exception ex) {
        log.warn("VHS replay job {} failed: {}", replayRunId, ex.getMessage());
        recordStatus(new JobStatus(replayRunId, "failed", null));
      }
    });
    return new ReplayRunResponse(replayRunId, "accepted", properties.target(),
            properties.mode().name(), statusUrl, null);
  }

  @NonNull
  public Optional<JobStatus> status(@NonNull String replayRunId) {
    synchronized (statuses) {
      return Optional.ofNullable(statuses.get(replayRunId));
    }
  }

  /**
   * Visible for testing: current number of tracked statuses (in-flight + finished).
   */
  int statusCount() {
    synchronized (statuses) {
      return statuses.size();
    }
  }

  /**
   * Insert or replace a job status while keeping the map bounded. A late completion update for an
   * id that is already tracked (in-flight → finished) replaces the value in place and never evicts
   * the same id — so the final status of a still-tracked job is never silently lost. New ids that
   * are already finished may evict the oldest finished entry to honour the cap; new in-flight ids
   * never evict (overshoot tolerated, documented in the class javadoc).
   */
  private void recordStatus(@NonNull JobStatus status) {
    synchronized (statuses) {
      boolean isNewId = !statuses.containsKey(status.replayRunId());
      if (isNewId && isFinished(status.status()) && statuses.size() >= statusLimit()) {
        evictOldestFinished();
      }
      statuses.put(status.replayRunId(), status);
    }
  }

  private static boolean isFinished(@NonNull String state) {
    return "completed".equals(state) || "failed".equals(state);
  }

  private void evictOldestFinished() {
    Iterator<Map.Entry<String, JobStatus>> it = statuses.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, JobStatus> entry = it.next();
      if (isFinished(entry.getValue().status())) {
        it.remove();
        return;
      }
    }
    // No finished entry to evict: every tracked job is in-flight. Per the documented contract
    // we tolerate a temporary overshoot rather than dropping an in-flight job.
  }

  private int statusLimit() {
    int limit = baseProperties.statusLimit();
    return limit <= 0 ? CbsVhsReplayProperties.DEFAULT_STATUS_LIMIT : limit;
  }

  @NonNull
  private VhsReplayReport runEngine(@NonNull CbsVhsReplayProperties properties,
          @NonNull Path tapeFile) {
    var driver = VhsCallDrivers.resolve(properties);
    var engine = new VhsReplayEngine(properties, driver, new VhsTapeReader(objectMapper),
            millis -> Thread.sleep(millis));
    return engine.replay(tapeFile);
  }

  private boolean isInline(@NonNull CbsVhsReplayProperties properties) {
    return properties.mode() == ReplayMode.exact && isDryRun(properties.target());
  }

  private boolean isDryRun(@Nullable String target) {
    if (target == null) {
      return true;
    }
    String t = target.trim().toLowerCase();
    return t.isEmpty() || "dry-run".equals(t) || "dryrun".equals(t);
  }

  @NonNull
  private CbsVhsReplayProperties resolveProperties(@NonNull ReplayRunRequest request) {
    String target = orDefault(request.target(), baseProperties.target());
    ReplayMode mode = parseMode(request.mode(), baseProperties.mode());
    int copies = orDefault(request.copies(), baseProperties.copies());
    double speed = orDefault(request.speed(), baseProperties.speed());
    int concurrency = orDefault(request.concurrency(), baseProperties.concurrency());
    return new CbsVhsReplayProperties(
            baseProperties.enabled(),
            target,
            baseProperties.allowProduction(),
            mode,
            copies,
            speed,
            concurrency,
            baseProperties.maxDurationMs(),
            baseProperties.compareOutput(),
            baseProperties.minWaitMs(),
            baseProperties.requestTimeoutMs(),
            baseProperties.localBaseUrl(),
            baseProperties.faking());
  }

  @NonNull
  private ReplayMode parseMode(@Nullable String raw, @NonNull ReplayMode fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return ReplayMode.valueOf(raw.trim().toLowerCase());
    } catch (IllegalArgumentException ex) {
      return fallback;
    }
  }

  @NonNull
  private String orDefault(@Nullable String value, @NonNull String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private int orDefault(@Nullable Integer value, int fallback) {
    return value == null || value < 1 ? fallback : value;
  }

  private double orDefault(@Nullable Double value, double fallback) {
    return value == null || value <= 0 ? fallback : value;
  }

  /**
   * In-memory status of a replay job.
   */
  public record JobStatus(
          @NonNull String replayRunId,
          @NonNull String status,
          @Nullable VhsReplayReport report) {
  }
}
