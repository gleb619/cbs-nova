package cbs.nova.dsl;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Enhanced Mermaid diagram generator that includes more detailed information
 * and can visualize external calls when provided.
 */
public final class MermaidDiagramGenerator {

  private MermaidDiagramGenerator() {
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process) {
    return forProcess(process, null, null);
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process,
                                            @Nullable List<Map<String, Object>> externalCalls,
                                            @Nullable Map<String, Integer> callCounts) {
    var sb = new StringBuilder("graph TD\n");
    sb.append("  Start([Start]) --> Execute[").append(process.name()).append("]\n");
    
    // Add external calls as sub-processes if provided
    if (externalCalls != null && !externalCalls.isEmpty()) {
      int callIndex = 0;
      for (Map<String, Object> call : externalCalls) {
        String callType = (String) call.getOrDefault("type", "external");
        String callTarget = (String) call.getOrDefault("target", "unknown");
        String callOperation = (String) call.getOrDefault("operation", "call");
        
        // Truncate long targets for readability
        String displayTarget = callTarget.length() > 20 ? 
                callTarget.substring(0, 17) + "..." : callTarget;
        
        sb.append("  Execute --> |").append(callOperation).append("| ").append(callType)
          .append(callIndex).append("[")
          .append(callType.toUpperCase()).append(": ").append(displayTarget)
          .append("]\n");
        callIndex++;
      }
    }
    
    if (process.compensationLogic() != null) {
      sb.append("  Execute --> |success| End([End])\n");
      sb.append("  Execute --> |failure| Compensate[Compensate]\n");
      sb.append("  Compensate --> End");
    } else {
      sb.append("  Execute --> |success| End([End])\n");
      sb.append("  Execute --> |failure| Fail([Fail])");
    }
    
    // Add call counts as a note if provided
    if (callCounts != null && !callCounts.isEmpty()) {
      sb.append("\n  %% Call Counts: ");
      boolean first = true;
      for (Map.Entry<String, Integer> entry : callCounts.entrySet()) {
        if (!first) {
          sb.append(", ");
        }
        sb.append(entry.getKey()).append(": ").append(entry.getValue());
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
    var sb = new StringBuilder("graph TD\n");
    sb.append("  Start([Start]) --> Activity[").append(tx.name()).append("]\n");
    
    // Add external calls as sub-processes if provided
    if (externalCalls != null && !externalCalls.isEmpty()) {
      int callIndex = 0;
      for (Map<String, Object> call : externalCalls) {
        String callType = (String) call.getOrDefault("type", "external");
        String callTarget = (String) call.getOrDefault("target", "unknown");
        String callOperation = (String) call.getOrDefault("operation", "call");
        
        // Truncate long targets for readability
        String displayTarget = callTarget.length() > 20 ? 
                callTarget.substring(0, 17) + "..." : callTarget;
        
        sb.append("  Activity --> |").append(callOperation).append("| ").append(callType)
          .append(callIndex).append("[")
          .append(callType.toUpperCase()).append(": ").append(displayTarget)
          .append("]\n");
        callIndex++;
      }
    }
    
    if (tx.compensationLogic() != null) {
      sb.append("  Activity --> |success| End([End])\n");
      sb.append("  Activity --> |failure| Compensate[Compensate]\n");
      sb.append("  Compensate --> End");
    } else {
      sb.append("  Activity --> |success| End([End])\n");
      sb.append("  Activity --> |failure| Fail([Fail])");
    }
    
    // Add call counts as a note if provided
    if (callCounts != null && !callCounts.isEmpty()) {
      sb.append("\n  %% Call Counts: ");
      boolean first = true;
      for (Map.Entry<String, Integer> entry : callCounts.entrySet()) {
        if (!first) {
          sb.append(", ");
        }
        sb.append(entry.getKey()).append(": ").append(entry.getValue());
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
    var sb = new StringBuilder("graph TD\n");
    sb.append("  Start([Start]) --> Helper[").append(name).append("]\n");
    
    // Add external calls as sub-processes if provided
    if (externalCalls != null && !externalCalls.isEmpty()) {
      int callIndex = 0;
      for (Map<String, Object> call : externalCalls) {
        String callType = (String) call.getOrDefault("type", "external");
        String callTarget = (String) call.getOrDefault("target", "unknown");
        String callOperation = (String) call.getOrDefault("operation", "call");
        
        // Truncate long targets for readability
        String displayTarget = callTarget.length() > 20 ? 
                callTarget.substring(0, 17) + "..." : callTarget;
        
        sb.append("  Helper --> |").append(callOperation).append("| ").append(callType)
          .append(callIndex).append("[")
          .append(callType.toUpperCase()).append(": ").append(displayTarget)
          .append("]\n");
        callIndex++;
      }
    }
    
    sb.append("  Helper --> End([End])");
    
    // Add call counts as a note if provided
    if (callCounts != null && !callCounts.isEmpty()) {
      sb.append("\n  %% Call Counts: ");
      boolean first = true;
      for (Map.Entry<String, Integer> entry : callCounts.entrySet()) {
        if (!first) {
          sb.append(", ");
        }
        sb.append(entry.getKey()).append(": ").append(entry.getValue());
        first = false;
      }
    }
    
    return sb.toString();
  }
}
