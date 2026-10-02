public class ScheduleGuardModels {

  /**
   * Inbound payload for a Temporal schedule pre-flight check.
   *
   * <p>{@code scheduleName} is echoed so the workflow can audit which operator-supplied
   * expression failed or passed. {@code expression} is the cron string to validate and
   * preview; the DSL supports the same forms the {@code cronParse} helper accepts
   * (6-field DST-safe, 5-field Unix, and {@code @macro} shorthands like {@code @daily}).
   * {@code fixedAfter} pins the "now" reference so the preview is deterministic and
   * independent of wall-clock time. {@code previewCount} controls how many upcoming fire
   * times the {@code nextFire} call returns (must be {@code >= 1}).
   */
  public record ScheduleGuardIn(
      String scheduleName,
      String expression,
      String fixedAfter,
      int previewCount) {
  }

  /**
   * Guard verdict. {@code valid} mirrors the {@code cronParse} flag (true when the
   * expression parses and the next-fire computation succeeds). {@code fireTimes} is the
   * ordered list of ISO-8601 instants the schedule would fire on, or {@code null} if the
   * helper refused. {@code errorMessage} carries a human-readable failure description
   * so a caller can present it back to the operator; it is {@code null} on success.
   */
  public record ScheduleGuardOut(
      String scheduleName,
      String expression,
      boolean valid,
      java.util.List<String> fireTimes,
      String errorMessage) {
  }
}