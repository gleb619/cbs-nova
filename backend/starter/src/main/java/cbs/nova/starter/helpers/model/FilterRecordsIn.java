package cbs.nova.starter.helpers.model;

import java.util.List;
import java.util.Map;

public record FilterRecordsIn(List<Map<String, Object>> records, String field, Object value) {
}
