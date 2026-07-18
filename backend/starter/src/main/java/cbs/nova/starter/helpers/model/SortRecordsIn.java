package cbs.nova.starter.helpers.model;

import java.util.List;
import java.util.Map;

public record SortRecordsIn(List<Map<String, Object>> records, String field, boolean ascending) {

  public SortRecordsIn {
    ascending = ascending;
  }

  public SortRecordsIn(List<Map<String, Object>> records, String field) {
    this(records, field, true);
  }
}
