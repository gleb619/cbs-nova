package cbs.nova.starter.reporting;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.generator.BpmnDiagramGenerator;
import cbs.nova.dsl.generator.DiagramGenerator;
import cbs.nova.dsl.generator.MermaidDiagramGenerator;
import cbs.nova.dsl.generator.PlantUmlDiagramGenerator;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Renders diagram strings from an {@link ExplainReport}. The report carries an AST/tree and
 * metadata; this layer converts that tree into mermaid, PlantUML and BPMN representations on demand
 * so the runtime does not need to know about diagram formats.
 */
public final class ExplainDiagramRenderer {

  private final DiagramGenerator mermaid = new MermaidDiagramGenerator();
  private final DiagramGenerator plantUml = new PlantUmlDiagramGenerator();
  private final DiagramGenerator bpmn = new BpmnDiagramGenerator();

  public @NonNull String mermaidDiagram(@NonNull ExplainReport report) {
    return render(report, mermaid);
  }

  public @NonNull String plantUmlDiagram(@NonNull ExplainReport report) {
    return render(report, plantUml);
  }

  public @NonNull String bpmnXml(@NonNull ExplainReport report) {
    return render(report, bpmn);
  }

  private @NonNull String render(@NonNull ExplainReport report,
          @NonNull DiagramGenerator generator) {
    GlobalManager gm = GlobalManager.globalManager();
    String name = report.name();
    List<Map<String, Object>> calls = report.externalCalls();
    Map<String, Integer> counts = report.callCounts();
    DslDescriptor descriptor = report.dslDescriptor();

    if (descriptor != null) {
      return switch (descriptor.type()) {
        case PROCESS -> gm.findProcess(name)
                .map(p -> generator.forProcess(p, calls, counts))
                .orElseGet(() -> generator.forHelper(name, calls, counts));
        case TRANSACTION -> gm.findTransaction(name)
                .map(t -> generator.forTransaction(t, calls, counts))
                .orElseGet(() -> generator.forHelper(name, calls, counts));
        default -> generator.forHelper(name, calls, counts);
      };
    }

    if (gm.findProcess(name).isPresent()) {
      return gm.findProcess(name)
              .map(p -> generator.forProcess(p, calls, counts))
              .orElseGet(() -> generator.forHelper(name, calls, counts));
    }
    if (gm.findTransaction(name).isPresent()) {
      return gm.findTransaction(name)
              .map(t -> generator.forTransaction(t, calls, counts))
              .orElseGet(() -> generator.forHelper(name, calls, counts));
    }
    return generator.forHelper(name, calls, counts);
  }
}
