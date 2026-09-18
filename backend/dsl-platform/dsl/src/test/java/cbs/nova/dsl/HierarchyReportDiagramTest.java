package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.generator.BpmnDiagramGenerator;
import cbs.nova.dsl.generator.MermaidDiagramGenerator;
import cbs.nova.dsl.generator.PlantUmlDiagramGenerator;
import cbs.nova.dsl.model.HierarchyReport;
import cbs.nova.dsl.model.ObjectDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Snapshot-style equivalence: an {@link HierarchyReport} built from the same fields a live
 * process/transaction/helper exposes must render to exactly the diagram strings the generators
 * produce for that live object, in all three formats.
 */
class HierarchyReportDiagramTest {

  private static final List<Map<String, Object>> CALLS = List.of(
          Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"));
  private static final Map<String, Integer> COUNTS = Map.of("http", 2);

  private final MermaidDiagramGenerator mermaid = new MermaidDiagramGenerator();
  private final PlantUmlDiagramGenerator plantUml = new PlantUmlDiagramGenerator();
  private final BpmnDiagramGenerator bpmn = new BpmnDiagramGenerator();

  @Test
  void processReportMatchesGeneratorOutput() {
    var process = Dsl.process("LoanDisbursement")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation((ctx, history) -> ctx.log("rolled back"))
            .build();
    var report = reportOf(process.name(), process.descriptor().type(), true);

    assertThat(report.toMermaid()).isEqualTo(mermaid.forProcess(process, CALLS, COUNTS));
    assertThat(report.toPlantUml()).isEqualTo(plantUml.forProcess(process, CALLS, COUNTS));
    assertThat(report.toBpmn()).isEqualTo(bpmn.forProcess(process, CALLS, COUNTS));
  }

  @Test
  void transactionReportMatchesGeneratorOutput() {
    var tx = Dsl.transaction("KycCheck")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    var report = reportOf(tx.name(), tx.descriptor().get().type(), false);

    assertThat(report.toMermaid()).isEqualTo(mermaid.forTransaction(tx, CALLS, COUNTS));
    assertThat(report.toPlantUml()).isEqualTo(plantUml.forTransaction(tx, CALLS, COUNTS));
    assertThat(report.toBpmn()).isEqualTo(bpmn.forTransaction(tx, CALLS, COUNTS));
  }

  @Test
  void helperReportMatchesGeneratorOutput() {
    var report = reportOf("MyHelper", DslObject.DslType.OTHER, false);

    assertThat(report.toMermaid()).isEqualTo(mermaid.forHelper("MyHelper", CALLS, COUNTS));
    assertThat(report.toPlantUml()).isEqualTo(plantUml.forHelper("MyHelper", CALLS, COUNTS));
    assertThat(report.toBpmn()).isEqualTo(bpmn.forHelper("MyHelper", CALLS, COUNTS));
  }

  @Test
  void forReportOverloadDelegatesToReportRendering() {
    var process = Dsl.process("LoanDisbursement")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    var report = reportOf(process.name(), process.descriptor().type(), false);

    assertThat(mermaid.forReport(report)).isEqualTo(report.toMermaid());
    assertThat(plantUml.forReport(report)).isEqualTo(report.toPlantUml());
    assertThat(bpmn.forReport(report)).isEqualTo(report.toBpmn());
  }

  private static HierarchyReport reportOf(String name, DslObject.DslType kind,
          boolean hasCompensation) {
    return new HierarchyReport(
            name,
            name,
            List.of(),
            CALLS,
            COUNTS,
            hasCompensation,
            null,
            DslDescriptor.builder().objectDescriptor(new ObjectDescriptor() {
              @Override
              public String name() {
                return name;
              }

              @Override
              public DslObject.DslType type() {
                return kind;
              }

              @Override
              public String description() {
                return null;
              }

              @Override
              public Class<?> inputType() {
                return null;
              }

              @Override
              public Class<?> outputType() {
                return null;
              }
            }).build(),
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            null);
  }
}
