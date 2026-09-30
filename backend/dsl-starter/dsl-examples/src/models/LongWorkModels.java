public class LongWorkModels {

  public record LongWorkIn(String taskId, int steps, long stepDelayMs) {
  }

  public record LongWorkOut(String taskId, String status, int stepsCompleted) {
  }
}