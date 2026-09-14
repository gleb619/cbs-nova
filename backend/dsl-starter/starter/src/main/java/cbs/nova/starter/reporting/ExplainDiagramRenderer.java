package cbs.nova.starter.reporting;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.generator.BpmnDiagramGenerator;
import cbs.nova.dsl.generator.DiagramGenerator;
import cbs.nova.dsl.generator.MermaidDiagramGenerator;
import cbs.nova.dsl.generator.PlantUmlDiagramGenerator;
import cbs.nova.dsl.model.ExplainGraphReport;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Renders diagram strings for {@link ExplainGraphReport}s and registered entities. A report renders
 * itself from its own fields ({@link ExplainGraphReport#toMermaid()} and siblings), so report
 * rendering works regardless of registry state; only {@link #renderByName(String, String)} consults
 * the live registry, for the introspection endpoint.
 */
@Service
public class ExplainDiagramRenderer {

  private final DiagramGenerator mermaid = new MermaidDiagramGenerator();
  private final DiagramGenerator plantUml = new PlantUmlDiagramGenerator();
  private final DiagramGenerator bpmn = new BpmnDiagramGenerator();

  public @NonNull String mermaidDiagram(@NonNull ExplainGraphReport report) {
    return report.toMermaid();
  }

  public @NonNull String plantUmlDiagram(@NonNull ExplainGraphReport report) {
    return report.toPlantUml();
  }

  public @NonNull String bpmnXml(@NonNull ExplainGraphReport report) {
    return report.toBpmn();
  }

  /**
   * Renders a diagram for a known process/transaction by name without requiring a precomputed
   * {@link ExplainGraphReport}. The {@code format} is one of {@code mermaid}, {@code plantuml}, or
   * {@code bpmn} (case-insensitive); any other value defaults to mermaid. Returns {@code null} when
   * no matching process/transaction is registered.
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
    String normalized = format.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "plantuml" -> plantUml;
      case "bpmn" -> bpmn;
      default -> mermaid;
    };
  }
}
