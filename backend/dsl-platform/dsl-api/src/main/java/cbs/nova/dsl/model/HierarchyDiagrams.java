package cbs.nova.dsl.model;

import cbs.nova.dsl.DslObject.DslType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Diagram rendering for {@link HierarchyReport} graphs and nodes. Whole-graph methods walk the
 * report graph (cycle-safe, breadth-first, keyed by node name) and render one diagram per node,
 * concatenated in walk order. Node-level methods render a single node from the same fields the
 * report carries, and are what the dsl-module {@code DiagramGenerator} implementations delegate to,
 * so a live process/transaction/helper and an equivalent report render to identical strings.
 */
public final class HierarchyDiagrams {

  private HierarchyDiagrams() {
  }

  public static @NonNull String mermaid(@NonNull HierarchyReport report) {
    return render(report, node -> MermaidGraphDiagram.render(kindOf(node), node.name(),
            node.hasCompensation(), node.externalCalls(), node.callCounts()));
  }

  public static @NonNull String plantUml(@NonNull HierarchyReport report) {
    return render(report, node -> PlantUmlGraphDiagram.render(kindOf(node), node.name(),
            node.hasCompensation(), node.externalCalls(), node.callCounts()));
  }

  public static @NonNull String bpmn(@NonNull HierarchyReport report) {
    return render(report, node -> BpmnGraphDiagram.render(kindOf(node), node.name(),
            node.hasCompensation(), node.externalCalls(), node.callCounts()));
  }

  public static @NonNull String mermaidNode(@NonNull DslType kind, @NonNull String name,
          boolean hasCompensation, @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return MermaidGraphDiagram.render(kind, name, hasCompensation, externalCalls, callCounts);
  }

  public static @NonNull String plantUmlNode(@NonNull DslType kind, @NonNull String name,
          boolean hasCompensation, @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return PlantUmlGraphDiagram.render(kind, name, hasCompensation, externalCalls, callCounts);
  }

  public static @NonNull String bpmnNode(@NonNull DslType kind, @NonNull String name,
          boolean hasCompensation, @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return BpmnGraphDiagram.render(kind, name, hasCompensation, externalCalls, callCounts);
  }

  static @NonNull DslType kindOf(@NonNull HierarchyReport report) {
    return report.dslDescriptor() != null ? report.dslDescriptor().type() : DslType.OTHER;
  }

  private static @NonNull String render(@NonNull HierarchyReport report,
          @NonNull Function<HierarchyReport, String> nodeRenderer) {
    return GraphWalk.breadthFirst(report, HierarchyReport::children, HierarchyReport::name)
            .stream()
            .map(nodeRenderer)
            .collect(Collectors.joining("\n\n"));
  }
}
