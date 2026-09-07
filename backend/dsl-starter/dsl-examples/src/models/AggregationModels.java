import java.util.List;
import java.util.Map;

public class AggregationModels {

  public record AggregationIn(List<Map<String, Object>> records, String valueField,
      String groupField) {
  }

  public record AggregationOut(List<Object> prices, Map<String, List<Map<String, Object>>> groups,
      double mean, double max) {
  }
}
