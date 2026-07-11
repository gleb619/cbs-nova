package cbs.nova.dsl.generator;

import cbs.nova.dsl.DiagramGenerator;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.utils.Substitutor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MermaidDiagramGenerator implements DiagramGenerator {

  private static String buildCallLines(@Nullable List<Map<String, Object>> calls, String fromNode) {
    if (calls == null || calls.isEmpty()) {
      return "";
    }
    var lineTemplate = """
            ${from} --> |${operation}| ${type}${index}[${typeUpper}: ${displayTarget}]
            """.indent(2);
    int[] index = {0};
    return calls.stream()
            .map(call -> {
              String callType = (String) call.getOrDefault("type", "external");
              String callTarget = (String) call.getOrDefault("target", "unknown");
              String callOperation = (String) call.getOrDefault("operation", "call");
              String displayTarget = callTarget.length() > 20
                      ? callTarget.substring(0, 17) + "..."
                      : callTarget;
              return Substitutor.format(lineTemplate, Map.of(
                      "from", fromNode,
                      "operation", callOperation,
                      "type", callType,
                      "index", String.valueOf(index[0]++),
                      "typeUpper", callType.toUpperCase(),
                      "displayTarget", displayTarget));
            })
            .collect(Collectors.joining());
  }

  private static String processBranches(boolean hasCompensation) {
    if (hasCompensation) {
      return """
              Execute --> |success| End([End])
              Execute --> |failure| Compensate[Compensate]
              Compensate --> End
              """.indent(2).stripTrailing();
    }
    return """
            Execute --> |success| End([End])
            Execute --> |failure| Fail([Fail])
            """.indent(2).stripTrailing();
  }

  private static String activityBranches(boolean hasCompensation) {
    if (hasCompensation) {
      return """
              Activity --> |success| End([End])
              Activity --> |failure| Compensate[Compensate]
              Compensate --> End
              """.indent(2).stripTrailing();
    }
    return """
            Activity --> |success| End([End])
            Activity --> |failure| Fail([Fail])
            """.indent(2).stripTrailing();
  }

  private static String buildCallCounts(@Nullable Map<String, Integer> callCounts) {
    if (callCounts == null || callCounts.isEmpty()) {
      return "";
    }
    return "\n  %% Call Counts: " + callCounts.entrySet().stream()
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
            graph TD
              Start([Start]) --> Execute[${process}]
            ${externalCalls}${branches}${callCounts}""";
    return Substitutor.format(template, Map.of(
            "process", process.name(),
            "externalCalls", buildCallLines(externalCalls, "Execute"),
            "branches", processBranches(process.compensationLogic() != null),
            "callCounts", buildCallCounts(callCounts)));
  }

  public @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    return forTransaction(tx, null, null);
  }

  public @NonNull String forTransaction(@NonNull TransactionDslObject tx,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var template = """
            graph TD
              Start([Start]) --> Activity[${tx}]
            ${externalCalls}${branches}${callCounts}""";
    return Substitutor.format(template, Map.of(
            "tx", tx.name(),
            "externalCalls", buildCallLines(externalCalls, "Activity"),
            "branches", activityBranches(tx.compensationLogic() != null),
            "callCounts", buildCallCounts(callCounts)));
  }

  public @NonNull String forHelper(@NonNull String name) {
    return forHelper(name, null, null);
  }

  public @NonNull String forHelper(@NonNull String name,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var template = """
            graph TD
              Start([Start]) --> Helper[${name}]
            ${externalCalls}  Helper --> End([End])${callCounts}""";
    return Substitutor.format(template, Map.of(
            "name", name,
            "externalCalls", buildCallLines(externalCalls, "Helper"),
            "callCounts", buildCallCounts(callCounts)));
  }
}
