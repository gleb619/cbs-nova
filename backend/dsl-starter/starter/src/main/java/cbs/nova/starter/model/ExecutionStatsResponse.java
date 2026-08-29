package cbs.nova.starter.model;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.persistence.DslRunStats;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Wire shape of {@code GET /api/executions/stats}.
 *
 * <p>
 * Status keys use the same display casing as {@link ExecutionDto} (e.g. {@code Running},
 * {@code Completed}) so the frontend renders them through the same status components without a
 * second mapping layer.
 */
public record ExecutionStatsResponse(
        long totalRuns,
        Map<String, Long> statusCounts,
        long windowRuns,
        long windowFailedRuns,
        double windowFailureRate,
        long windowHours,
        java.util.List<ProcessRunCount> topProcesses) {

  public record ProcessRunCount(String processName, long runCount) {
  }

  public static ExecutionStatsResponse from(DslRunStats stats, long windowHours) {
    Map<String, Long> displayCounts = new LinkedHashMap<>();
    stats.statusCounts()
            .forEach((status, count) -> displayCounts.put(displayStatus(status), count));
    return new ExecutionStatsResponse(
            stats.totalRuns(),
            displayCounts,
            stats.windowRuns(),
            stats.windowFailedRuns(),
            stats.windowFailureRate(),
            windowHours,
            stats.topProcesses().stream()
                    .map(p -> new ProcessRunCount(p.processName(), p.runCount()))
                    .toList());
  }

  private static String displayStatus(String status) {
    if (status == null || status.isBlank()) {
      return "Running";
    }
    for (DslRunStatus candidate : DslRunStatus.values()) {
      if (candidate.name().equals(status)) {
        return candidate.name().charAt(0) + candidate.name().substring(1).toLowerCase(Locale.ROOT);
      }
    }
    return status.charAt(0) + status.substring(1).toLowerCase(Locale.ROOT);
  }
}
