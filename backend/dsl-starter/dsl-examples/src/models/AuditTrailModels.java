public class AuditTrailModels {

  public record AuditTrailIn(
          String eventType,
          String actor,
          String zone) {
  }

  public record AuditTrailOut(
          String eventType,
          String actor,
          String utcTimestamp,
          String localTimestamp,
          String zone) {
  }
}