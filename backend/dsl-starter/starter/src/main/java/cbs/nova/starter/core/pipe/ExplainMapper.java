package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.HierarchyDiagrams;
import cbs.nova.dsl.model.HierarchyReport;
import java.util.List;
import org.jspecify.annotations.NonNull;

public final class ExplainMapper {

  private ExplainMapper() {
  }

  public static @NonNull ExplainReport fromHierarchy(@NonNull HierarchyReport hierarchy) {
    String mermaid = HierarchyDiagrams.mermaidNode(
            HierarchyDiagrams.kindOf(hierarchy),
            hierarchy.name(),
            hierarchy.hasCompensation(),
            hierarchy.externalCalls(),
            hierarchy.callCounts());
    List<ExplainReport> childReports = hierarchy.children().stream()
            .map(ExplainMapper::fromHierarchy)
            .toList();
    return new ExplainReport(
            hierarchy.name(),
            hierarchy.description(),
            mermaid,
            childReports);
  }
}
