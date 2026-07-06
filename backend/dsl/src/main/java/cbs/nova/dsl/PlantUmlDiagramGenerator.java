package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Enhanced PlantUML diagram generator that includes more detailed information and can visualize
 * external calls when provided.
 */
public final class PlantUmlDiagramGenerator {

  private PlantUmlDiagramGenerator() {
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process) {
    return forProcess(process, null, null);
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var sb = new StringBuilder("@startuml\n");
    sb.append("start\n");
    sb.append(":").append(process.name()).append(";\n");

    // Add external calls as steps if provided
    if (externalCalls != null && !externalCalls.isEmpty()) {
      for (Map<String, Object> call : externalCalls) {
        String callType = (String) call.getOrDefault("type", "external");
        String callTarget = (String) call.getOrDefault("target", "unknown");
        String callOperation = (String) call.getOrDefault("operation", "call");

        // Truncate long targets for readability
        String displayTarget = callTarget.length() > 30
                ? callTarget.substring(0, 27) + "..."
                : callTarget;

        sb.append(":").append(callType.toUpperCase()).append(" ").append(callOperation)
                .append(" (").append(displayTarget).append(");\n");
      }
    }

    if (process.compensationLogic() != null) {
      sb.append("if (success?) then (yes)\n");
      sb.append("else (no)\n");
      sb.append("  :Compensate;\n");
      sb.append("endif\n");
    }
    sb.append("stop\n");
    sb.append("@endum");

    // Add call counts as a comment if provided
    if (callCounts != null && !callCounts.isEmpty()) {
      sb.insert(sb.lastIndexOf("@endum"), "\n' Call Counts: ");
      boolean first = true;
      for (Map.Entry<String, Integer> entry : callCounts.entrySet()) {
        if (!first) {
          sb.insert(sb.lastIndexOf("@endum"), ", ");
        }
        sb.insert(sb.lastIndexOf("@endum"), entry.getKey() + ": " + entry.getValue());
        first = false;
      }
    }

    return sb.toString();
  }

  public static @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    return forTransaction(tx, null, null);
  }

  public static @NonNull String forTransaction(@NonNull TransactionDslObject tx,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var sb = new StringBuilder("@startuml\n");
    sb.append("start\n");
    sb.append(":").append(tx.name()).append(";\n");

    // Add external calls as steps if provided
    if (externalCalls != null && !externalCalls.isEmpty()) {
      for (Map<String, Object> call : externalCalls) {
        String callType = (String) call.getOrDefault("type", "external");
        String callTarget = (String) call.getOrDefault("target", "unknown");
        String callOperation = (String) call.getOrDefault("operation", "call");

        // Truncate long targets for readability
        String displayTarget = callTarget.length() > 30
                ? callTarget.substring(0, 27) + "..."
                : callTarget;

        sb.append(":").append(callType.toUpperCase()).append(" ").append(callOperation)
                .append(" (").append(displayTarget).append(");\n");
      }
    }

    if (tx.compensationLogic() != null) {
      sb.append("if (success?) then (yes)\n");
      sb.append("else (no)\n");
      sb.append("  :Compensate;\n");
      sb.append("endif\n");
    }
    sb.append("stop\n");
    sb.append("@endum");

    // Add call counts as a comment if provided
    if (callCounts != null && !callCounts.isEmpty()) {
      sb.insert(sb.lastIndexOf("@endum"), "\n' Call Counts: ");
      boolean first = true;
      for (Map.Entry<String, Integer> entry : callCounts.entrySet()) {
        if (!first) {
          sb.insert(sb.lastIndexOf("@endum"), ", ");
        }
        sb.insert(sb.lastIndexOf("@endum"), entry.getKey() + ": " + entry.getValue());
        first = false;
      }
    }

    return sb.toString();
  }

  public static @NonNull String forHelper(@NonNull String name) {
    return forHelper(name, null, null);
  }

  public static @NonNull String forHelper(@NonNull String name,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var sb = new StringBuilder("@startuml\n");
    sb.append("start\n");
    sb.append(":").append(name).append(";\n");

    // Add external calls as steps if provided
    if (externalCalls != null && !externalCalls.isEmpty()) {
      for (Map<String, Object> call : externalCalls) {
        String callType = (String) call.getOrDefault("type", "external");
        String callTarget = (String) call.getOrDefault("target", "unknown");
        String callOperation = (String) call.getOrDefault("operation", "call");

        // Truncate long targets for readability
        String displayTarget = callTarget.length() > 30
                ? callTarget.substring(0, 27) + "..."
                : callTarget;

        sb.append(":").append(callType.toUpperCase()).append(" ").append(callOperation)
                .append(" (").append(displayTarget).append(");\n");
      }
    }

    sb.append("stop\n");
    sb.append("@endum");

    // Add call counts as a comment if provided
    if (callCounts != null && !callCounts.isEmpty()) {
      sb.insert(sb.lastIndexOf("@endum"), "\n' Call Counts: ");
      boolean first = true;
      for (Map.Entry<String, Integer> entry : callCounts.entrySet()) {
        if (!first) {
          sb.insert(sb.lastIndexOf("@endum"), ", ");
        }
        sb.insert(sb.lastIndexOf("@endum"), entry.getKey() + ": " + entry.getValue());
        first = false;
      }
    }

    return sb.toString();
  }
}
