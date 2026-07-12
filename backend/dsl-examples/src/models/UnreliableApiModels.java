import cbs.nova.starter.helpers.model.UnreliableApiIn;
import java.util.List;

public class UnreliableApiModels {

  public record UnreliableProcessIn(String scenario, UnreliableApiIn apiCall) {
  }

  public record UnreliableProcessOut(String scenario, String status, List<String> logs) {
  }
}
