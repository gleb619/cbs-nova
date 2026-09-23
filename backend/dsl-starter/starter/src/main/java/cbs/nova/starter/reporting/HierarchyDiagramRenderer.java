package cbs.nova.starter.reporting;

import cbs.nova.dsl.model.HierarchyDiagrams;
import cbs.nova.dsl.model.HierarchyReport;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

/**
 * Renders diagram strings for {@link HierarchyReport}s. A report renders itself from its own fields
 * ({@link HierarchyReport#toMermaid()} and siblings), so rendering works regardless of registry
 * state.
 */
@Service
public class HierarchyDiagramRenderer {

  public @NonNull String mermaidDiagram(@NonNull HierarchyReport report) {
    return report.toMermaid();
  }

  public @NonNull String plantUmlDiagram(@NonNull HierarchyReport report) {
    return report.toPlantUml();
  }

  public @NonNull String bpmnXml(@NonNull HierarchyReport report) {
    return report.toBpmn();
  }

  public @NonNull String mermaidNode(@NonNull HierarchyReport report) {
    return HierarchyDiagrams.mermaidNode(HierarchyDiagrams.kindOf(report),
            report.name(), report.hasCompensation(), report.externalCalls(), report.callCounts());
  }
}
