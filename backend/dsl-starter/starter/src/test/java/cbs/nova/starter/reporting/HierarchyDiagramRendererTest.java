package cbs.nova.starter.reporting;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.model.ObjectDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.HierarchyReport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HierarchyDiagramRendererTest {

  private final HierarchyDiagramRenderer renderer = new HierarchyDiagramRenderer();

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

    HierarchyReport report = new HierarchyReport(
            processName,
            "Process: " + processName,
            List.of(),
            List.of(),
            Map.of(),
            false,
            new ExecutableDescriptor(processName, null, String.class, String.class,
                    List.of()),
            DslDescriptor.builder()
                    .objectDescriptor(new ObjectDescriptor() {
                      @Override
                      public String name() {
                        return processName;
                      }

                      @Override
                      public DslObject.DslType type() {
                        return DslType.PROCESS;
                      }

                      @Override
                      public String description() {
                        return null;
                      }

                      @Override
                      public Class<?> inputType() {
                        return String.class;
                      }

                      @Override
                      public Class<?> outputType() {
                        return String.class;
                      }
                    })
                    .parameters(List.of())
                    .taskQueue(null)
                    .version(null)
                    .startToCloseTimeout(null)
                    .heartbeatTimeout(null)
                    .build(),
            null,
            List.of(),
            null,
            null,
            List.of(),
            null);

    String mermaid = renderer.mermaidDiagram(report);

    assertThat(mermaid).isNotBlank();
    assertThat(mermaid).contains("Execute[" + processName + "]");
  }

  @Test
  void reportRenderingDoesNotDependOnRegistryState() {
    String processName = "GoneProcess-" + System.nanoTime();
    HierarchyReport report = new HierarchyReport(
            processName,
            "Process: " + processName,
            List.of(),
            List.of(),
            Map.of(),
            false,
            null,
            DslDescriptor.builder()
                    .objectDescriptor(new ObjectDescriptor() {
                      @Override
                      public String name() {
                        return processName;
                      }

                      @Override
                      public DslObject.DslType type() {
                        return DslType.PROCESS;
                      }

                      @Override
                      public String description() {
                        return null;
                      }

                      @Override
                      public Class<?> inputType() {
                        return String.class;
                      }

                      @Override
                      public Class<?> outputType() {
                        return String.class;
                      }
                    })
                    .parameters(List.of())
                    .taskQueue(null)
                    .version(null)
                    .startToCloseTimeout(null)
                    .heartbeatTimeout(null)
                    .build(),
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            null);

    assertThat(renderer.mermaidDiagram(report)).contains("Execute[" + processName + "]");
    assertThat(renderer.plantUmlDiagram(report)).contains(":" + processName + ";");
    assertThat(renderer.bpmnXml(report)).contains("name=\"" + processName + "\"");
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
