package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class MermaidDiagramGenerator {

  private MermaidDiagramGenerator() {
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process) {
    var sb = new StringBuilder("graph TD\n");
    sb.append("  Start([Start]) --> Execute[").append(process.name()).append("]\n");
    if (process.compensationLogic() != null) {
      sb.append("  Execute --> |success| End([End])\n");
      sb.append("  Execute --> |failure| Compensate[Compensate]\n");
      sb.append("  Compensate --> End");
    } else {
      sb.append("  Execute --> |success| End([End])\n");
      sb.append("  Execute --> |failure| Fail([Fail])");
    }
    return sb.toString();
  }

  public static @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    var sb = new StringBuilder("graph TD\n");
    sb.append("  Start([Start]) --> Activity[").append(tx.name()).append("]\n");
    if (tx.compensationLogic() != null) {
      sb.append("  Activity --> |success| End([End])\n");
      sb.append("  Activity --> |failure| Compensate[Compensate]\n");
      sb.append("  Compensate --> End");
    } else {
      sb.append("  Activity --> |success| End([End])\n");
      sb.append("  Activity --> |failure| Fail([Fail])");
    }
    return sb.toString();
  }

  public static @NonNull String forHelper(@NonNull String name) {
    return "graph TD\n  Start([Start]) --> Helper[" + name + "]\n  Helper --> End([End])";
  }
}
