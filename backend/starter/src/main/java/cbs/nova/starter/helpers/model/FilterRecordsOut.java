package cbs.nova.starter.helpers.model;

import java.util.List;
import java.util.Map;

public record FilterRecordsOut(List<Map<String, Object>> matched) {
}
