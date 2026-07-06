package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class PlantUmlDiagramGenerator {

  private PlantUmlDiagramGenerator() {
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process) {
    var sb = new StringBuilder("@startuml\n");
    sb.append("start\n");
    sb.append(":").append(process.name()).append(";\n");
    if (process.compensationLogic() != null) {
      sb.append("if (success?) then (yes)\n");
      sb.append("else (no)\n");
      sb.append("  :Compensate;\n");
      sb.append("endif\n");
    }
    sb.append("stop\n");
    sb.append("@endum");
    return sb.toString();
  }

  public static @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    var sb = new StringBuilder("@startuml\n");
    sb.append("start\n");
    sb.append(":").append(tx.name()).append(";\n");
    if (tx.compensationLogic() != null) {
      sb.append("if (success?) then (yes)\n");
      sb.append("else (no)\n");
      sb.append("  :Compensate;\n");
      sb.append("endif\n");
    }
    sb.append("stop\n");
    sb.append("@endum");
    return sb.toString();
  }

  public static @NonNull String forHelper(@NonNull String name) {
    return "@startuml\nstart\n:" + name + ";\nstop\n@endum";
  }
}
