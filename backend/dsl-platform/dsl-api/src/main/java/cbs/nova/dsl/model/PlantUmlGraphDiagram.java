package cbs.nova.dsl.model;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.utils.Substitutor;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class PlantUmlGraphDiagram {

  private PlantUmlGraphDiagram() {
  }

  static @NonNull String render(@NonNull DslType kind, @NonNull String name,
          boolean hasCompensation, @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return switch (kind) {
      case PROCESS, TRANSACTION -> activity(name, hasCompensation, externalCalls, callCounts);
      default -> helper(name, externalCalls, callCounts);
    };
  }

  private static String buildExternalCallLines(@Nullable List<Map<String, Object>> externalCalls) {
    if (externalCalls == null || externalCalls.isEmpty()) {
      return "";
    }
    var template = """
            :${type} ${operation} (${target});
            """;
    return externalCalls.stream()
            .map(call -> Substitutor.format(template, Map.of(
                    "type", callType(call),
                    "operation", callOperation(call),
                    "target", displayTarget(call))))
            .collect(Collectors.joining());
  }

  private static String callType(Map<String, Object> call) {
    return ((String) call.getOrDefault("type", "external")).toUpperCase();
  }

  private static String callOperation(Map<String, Object> call) {
    return (String) call.getOrDefault("operation", "call");
  }

  private static String displayTarget(Map<String, Object> call) {
    String target = (String) call.getOrDefault("target", "unknown");
    return target.length() > 30
            ? target.substring(0, 27) + "..."
            : target;
  }

  private static String buildCompensation(boolean hasCompensation) {
    if (!hasCompensation) {
      return "";
    }
    return """
            if (success?) then (yes)
            else (no)
              :Compensate;
            endif
            """;
  }

  private static String buildCallCounts(@Nullable Map<String, Integer> callCounts) {
    if (callCounts == null || callCounts.isEmpty()) {
      return "";
    }
    return "\n' Call Counts: " + callCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining(", "));
  }

  private static @NonNull String activity(@NonNull String name, boolean hasCompensation,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var template = """
            @startuml
            start
            :${name};
            ${externalCalls}${compensation}stop
            ${callCounts}@enduml""";
    return Substitutor.format(template, Map.of(
            "name", name,
            "externalCalls", buildExternalCallLines(externalCalls),
            "compensation", buildCompensation(hasCompensation),
            "callCounts", buildCallCounts(callCounts)));
  }

  private static @NonNull String helper(@NonNull String name,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var template = """
            @startuml
            start
            :${name};
            ${externalCalls}stop
            ${callCounts}@enduml""";
    return Substitutor.format(template, Map.of(
            "name", name,
            "externalCalls", buildExternalCallLines(externalCalls),
            "callCounts", buildCallCounts(callCounts)));
  }
}
