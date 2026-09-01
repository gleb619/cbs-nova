package cbs.nova.starter.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExplainDiagramRendererTest {

  private final ExplainDiagramRenderer renderer = new ExplainDiagramRenderer();

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void mermaidDiagramForProcessReportIsNonBlank() {
    String processName = "SampleProcess-" + System.nanoTime();
    GlobalManager.globalManager()
            .registerProcess(Dsl.process(processName)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    ExplainReport report = new ExplainReport(
            processName,
            "Process: " + processName,
            List.of(),
            List.of(),
            Map.of(),
            new ExecutableDescriptor(processName, null, String.class, String.class, false, null,
                    List.of()),
            new DslDescriptor(processName, DslType.PROCESS, null, String.class,
                    String.class, false, false, null, List.of(), null, null, null, null),
            null,
            List.of(),
            null,
            null,
            null);

    String mermaid = renderer.mermaidDiagram(report);

    assertThat(mermaid).isNotBlank();
    assertThat(mermaid).contains("Execute[" + processName + "]");
  }

  @Test
  void renderByNameReturnsMermaidForRegisteredProcess() {
    String processName = "ByName-" + System.nanoTime();
    GlobalManager.globalManager()
            .registerProcess(Dsl.process(processName)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    String mermaid = renderer.renderByName(processName, "mermaid");

    assertThat(mermaid).isNotBlank();
    assertThat(mermaid).contains("Execute[" + processName + "]");
  }

  @Test
  void renderByNameReturnsNullForUnknownEntity() {
    assertThat(renderer.renderByName("nope-" + System.nanoTime(), "mermaid")).isNull();
  }

  @Test
  void renderByNameFallsBackToMermaidForUnknownFormat() {
    String processName = "Fallback-" + System.nanoTime();
    GlobalManager.globalManager()
            .registerProcess(Dsl.process(processName)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    String diagram = renderer.renderByName(processName, "graphviz");

    assertThat(diagram).isNotBlank();
    assertThat(diagram).contains("graph TD");
  }
}
