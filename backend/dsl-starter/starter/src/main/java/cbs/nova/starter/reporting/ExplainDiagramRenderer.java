package cbs.nova.starter.reporting;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.generator.DiagramGenerator;
import cbs.nova.dsl.generator.MermaidDiagramGenerator;
import cbs.nova.dsl.generator.PlantUmlDiagramGenerator;
import cbs.nova.dsl.generator.BpmnDiagramGenerator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Renders diagram strings from an {@link ExplainReport}. The report carries an AST/tree and
 * metadata; this service converts that tree into mermaid, PlantUML and BPMN representations on
 * demand so the runtime does not need to know about diagram formats.
 */
@Service
public class ExplainDiagramRenderer {

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

  /**
   * Renders a diagram for a known process/transaction/helper by name without requiring a
   * precomputed {@link ExplainReport}. The {@code format} is one of {@code mermaid},
   * {@code plantuml}, or {@code bpmn} (case-insensitive); any other value defaults to mermaid.
   * Returns {@code null} when no matching process/transaction is registered.
   */
  public @Nullable String renderByName(@NonNull String name,
          @NonNull String format) {
    GlobalManager gm = GlobalManager.globalManager();
    var process = gm.findProcess(name);
    if (process.isPresent()) {
      return pickGenerator(format).forProcess(process.get(), List.of(), Map.of());
    }
    var tx = gm.findTransaction(name);
    if (tx.isPresent()) {
      return pickGenerator(format).forTransaction(tx.get(), List.of(), Map.of());
    }
    return null;
  }

  private @NonNull DiagramGenerator pickGenerator(@NonNull String format) {
    String normalized = format.trim().toLowerCase(java.util.Locale.ROOT);
    return switch (normalized) {
      case "plantuml" -> plantUml;
      case "bpmn" -> bpmn;
      default -> mermaid;
    };
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
