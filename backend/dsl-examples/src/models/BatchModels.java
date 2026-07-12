
import java.util.List;

public class BatchModels {

  public record BatchIn(List<BatchItem> items) {
  }

  public record BatchItem(String id, int value) {
  }

  public record BatchOut(int total, String summary) {
  }
}
