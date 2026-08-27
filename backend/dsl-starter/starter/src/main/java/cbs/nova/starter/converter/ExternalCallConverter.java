package cbs.nova.starter.converter;

import cbs.nova.starter.core.recorder.ExternalCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExternalCallConverter {

  private ExternalCallConverter() {
  }

  //TODO: search and move to `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/StarterConstant.java` a string constants
  public static List<Map<String, Object>> toCallJson(List<ExternalCall> calls) {
    List<Map<String, Object>> callsJson = new ArrayList<>();
    for (ExternalCall call : calls) {
      Map<String, Object> callMap = new HashMap<>();
      callMap.put("type", call.type());
      callMap.put("target", call.target());
      callMap.put("operation", call.operation());
      callMap.put("timestamp", call.timestamp());
      callMap.put("metadata", call.metadata());
      callsJson.add(callMap);
    }
    return List.copyOf(callsJson);
  }

  public static Map<String, Integer> toCallCounts(List<ExternalCall> calls) {
    Map<String, Integer> counts = new HashMap<>();
    for (ExternalCall call : calls) {
      counts.merge(call.type(), 1, Integer::sum);
    }
    return Map.copyOf(counts);
  }
}
