
import java.util.List;
import java.util.Map;

public class HelperPipelineModels {

  public record PipelineIn(
      List<Map<String, Object>> records,
      String filterField,
      Object filterValue,
      String messageTemplate,
      String payloadJson,
      String extractPath) {
  }

  public record PipelineOut(
      int matchedCount,
      double total,
      String message,
      String extractedValue,
      boolean extracted) {
  }
}
