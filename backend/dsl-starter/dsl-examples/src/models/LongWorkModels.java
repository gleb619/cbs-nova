
public class LongWorkModels {

  public record LongWorkIn(String taskId, int steps) {
  }

  public record LongWorkOut(String taskId, String status, int stepsCompleted) {
  }
}
