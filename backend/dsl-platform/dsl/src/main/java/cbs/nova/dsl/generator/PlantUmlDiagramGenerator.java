package cbs.nova.dsl.generator;

import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.utils.Substitutor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PlantUmlDiagramGenerator implements DiagramGenerator {

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

  public @NonNull String forProcess(@NonNull ProcessDslObject process) {
    return forProcess(process, null, null);
  }

  public @NonNull String forProcess(@NonNull ProcessDslObject process,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var template = """
            @startuml
            start
            :${name};
            ${externalCalls}${compensation}stop
            ${callCounts}@enduml""";
    return Substitutor.format(template, Map.of(
            "name", process.name(),
            "externalCalls", buildExternalCallLines(externalCalls),
            "compensation", buildCompensation(process.compensationLogic() != null),
            "callCounts", buildCallCounts(callCounts)));
  }

  public @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    return forTransaction(tx, null, null);
  }

  public @NonNull String forTransaction(@NonNull TransactionDslObject tx,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var template = """
            @startuml
            start
            :${name};
            ${externalCalls}${compensation}stop
            ${callCounts}@enduml""";
    return Substitutor.format(template, Map.of(
            "name", tx.name(),
            "externalCalls", buildExternalCallLines(externalCalls),
            "compensation", buildCompensation(tx.compensationLogic() != null),
            "callCounts", buildCallCounts(callCounts)));
  }

  public @NonNull String forHelper(@NonNull String name) {
    return forHelper(name, null, null);
  }

  public @NonNull String forHelper(@NonNull String name,
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
