public class ScheduleWindowModels {

  public record ScheduleWindowIn(
      String jobName,
      String gracePeriod,
      String hardLimit) {
  }

  public record ScheduleWindowOut(
      String jobName,
      long graceMillis,
      long graceSeconds,
      String graceIso,
      long hardLimitMillis,
      long hardLimitSeconds,
      String hardLimitIso) {
  }
}
