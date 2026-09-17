import java.util.List;
import java.util.Map;

public class ListOpsModels {

  public record ListOpsDslIn(String categoryField, String countField) {
  }

  public record ListOpsDslOut(
      List<Object> categories,
      List<Object> distinctCategories,
      Map<Object, Long> categoryCounts) {
  }
}
