
import cbs.nova.starter.helpers.model.HttpCallIn;

public class HttpResilienceModels {

  public record HttpResilienceProcessIn(String scenario, HttpCallIn httpCall) {
  }

  public record HttpResilienceProcessOut(String scenario, String status, java.util.List<String> logs) {
  }
}
