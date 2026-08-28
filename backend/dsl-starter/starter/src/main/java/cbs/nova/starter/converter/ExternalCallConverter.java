package cbs.nova.starter.converter;

import cbs.nova.starter.core.StarterConstant;
import cbs.nova.starter.core.recorder.ExternalCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExternalCallConverter {

  private ExternalCallConverter() {
  }

  public static List<Map<String, Object>> toCallJson(List<ExternalCall> calls) {
    List<Map<String, Object>> callsJson = new ArrayList<>();
    for (ExternalCall call : calls) {
      Map<String, Object> callMap = new HashMap<>();
      callMap.put(StarterConstant.PAYLOAD_TYPE, call.type());
      callMap.put(StarterConstant.PAYLOAD_TARGET, call.target());
      callMap.put(StarterConstant.PAYLOAD_OPERATION, call.operation());
      callMap.put(StarterConstant.PAYLOAD_TIMESTAMP, call.timestamp());
      callMap.put(StarterConstant.PAYLOAD_METADATA, call.metadata());
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
