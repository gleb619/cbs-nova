import java.util.List;
import java.util.Map;

public class RecordOpsModels {

  public record RecordOpsIn(String category, boolean ascending, List<String> pickKeys) {
  }

  public record RecordOpsOut(
      List<Map<String, Object>> filtered,
      List<Map<String, Object>> sorted,
      List<Map<String, Object>> projected) {
  }
}