import java.util.List;
import java.util.Map;

public class AggregationModels {

  public record AggregationIn(List<Map<String, Object>> records, String valueField,
      String groupField, List<Map<String, Object>> mergeRecords) {
  }

  public record AggregationOut(List<Object> prices, Map<String, List<Map<String, Object>>> groups,
      double mean, double max, double total, Map<String, Object> minRecord,
      Map<String, Object> maxRecord, Map<String, Object> shallowMerged,
      Map<String, Object> deepMerged) {
  }
}
