package cbs.nova.dsl.generator;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.model.HierarchyDiagrams;
import cbs.nova.dsl.model.HierarchyReport;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class PlantUmlDiagramGenerator implements DiagramGenerator {

  public @NonNull String forProcess(@NonNull ProcessDslObject process) {
    return forProcess(process, null, null);
  }

  public @NonNull String forProcess(@NonNull ProcessDslObject process,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return HierarchyDiagrams.plantUmlNode(DslType.PROCESS, process.name(),
            process.compensationLogic() != null, externalCalls, callCounts);
  }

  public @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    return forTransaction(tx, null, null);
  }

  public @NonNull String forTransaction(@NonNull TransactionDslObject tx,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return HierarchyDiagrams.plantUmlNode(DslType.TRANSACTION, tx.name(),
            tx.compensationLogic() != null, externalCalls, callCounts);
  }

  public @NonNull String forHelper(@NonNull String name) {
    return forHelper(name, null, null);
  }

  public @NonNull String forHelper(@NonNull String name,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return HierarchyDiagrams.plantUmlNode(DslType.OTHER, name, false, externalCalls,
            callCounts);
  }

  @Override
  public @NonNull String forReport(@NonNull HierarchyReport report) {
    return report.toPlantUml();
  }
}
