package cbs.nova.starter.metric;

import cbs.nova.starter.core.StarterConstants;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.jspecify.annotations.NonNull;
import cbs.nova.dsl.history.DslRunStatus;
import org.jspecify.annotations.Nullable;

/**
 * Records the end-to-end duration of a production DSL run as a Micrometer histogram Timer.
 *
 * <p>
 * The timer is tagged with the terminal {@code status} and a coarser {@code outcome} bucket
 * ({@code ok} for {@code COMPLETED}, {@code error} otherwise) so latency SLOs can be expressed both
 * per-status and as ok/error aggregates without losing the original status dimension.
 */
public class RunDurationMetricsCollector {

  private static final String OUTCOME_OK = StarterConstants.OUTCOME_OK;
  private static final String OUTCOME_ERROR = StarterConstants.OUTCOME_ERROR;

  private final MeterRegistry meterRegistry;

  public RunDurationMetricsCollector(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Records the duration for a terminal run.
   *
   * @param processName
   *          the DSL process name; blank/null values are normalised to
   *          {@link StarterConstants#UNKNOWN_PROCESS}
   * @param status
   *          the terminal {@link cbs.nova.dsl.history.DslRunStatus} name
   * @param duration
   *          non-negative duration; negative values are clamped to zero
   */
  public void record(@Nullable String processName, @Nullable String status,
          @NonNull Duration duration) {
    String safe = safeProcessName(processName);
    String statusValue = status != null ? status : StarterConstants.UNKNOWN_PROCESS;
    String outcome = DslRunStatus.COMPLETED.name().equals(statusValue) ? OUTCOME_OK : OUTCOME_ERROR;
    Duration nonNegative = duration.isNegative() ? Duration.ZERO : duration;

    Timer.builder(StarterConstants.RUN_DURATION_TIMER)
            .description("Duration of a production DSL run")
            .publishPercentileHistogram(true)
            .serviceLevelObjectives(Duration.ofSeconds(60))
            .tag(StarterConstants.PROCESS_NAME_TAG, safe)
            .tag(StarterConstants.STATUS_TAG, statusValue)
            .tag(StarterConstants.OUTCOME_TAG, outcome)
            .register(meterRegistry)
            .record(nonNegative);
  }

  private static @NonNull String safeProcessName(@Nullable String processName) {
    if (processName == null || processName.isBlank()) {
      return StarterConstants.UNKNOWN_PROCESS;
    }
    return processName;
  }
}
