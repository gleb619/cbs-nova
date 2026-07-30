package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ExplainReportTest {

  @Test
  void accessorsExposeAllComponents() {
    Map<String, Object> call = Map.of("name", "log");
    Map<String, Integer> counts = Map.of("log", 2);
    var executable = new ExecutableDescriptor(
            "echo", "Echoes", String.class, String.class, false, null, List.of());
    var dsl = new DslDescriptor(
            "echo", DslObject.DslType.PROCESS, "Echoes", String.class, String.class,
            false, false, null, List.of(), null, null, null, null);

    var report = new ExplainReport(
            "echo",
            "Echoes input",
            "graph TD; A-->B;",
            "@startuml\nA->B\n@enduml",
            "<bpmn/>",
            List.of("step-1"),
            List.of(call),
            counts,
            executable,
            dsl,
            null,
            List.of(),
            null,
            List.of());

    assertThat(report.name()).isEqualTo("echo");
    assertThat(report.description()).isEqualTo("Echoes input");
    assertThat(report.mermaidDiagram()).isEqualTo("graph TD; A-->B;");
    assertThat(report.plantUmlDiagram()).contains("@startuml");
    assertThat(report.bpmnXml()).isEqualTo("<bpmn/>");
    assertThat(report.executionTrace()).containsExactly("step-1");
    assertThat(report.externalCalls()).containsExactly(call);
    assertThat(report.callCounts()).containsExactly(Map.entry("log", 2));
    assertThat(report.executableDescriptor()).isSameAs(executable);
    assertThat(report.dslDescriptor()).isSameAs(dsl);
    assertThat(report.astTree()).isNull();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void nullableDescriptorsAcceptNull() {
    var report = new ExplainReport(
            "n", "d", "m", "p", "b",
            List.of(), List.of(), Map.of(),
            null, null, null, List.of(), null, List.of());

    assertThat(report.executableDescriptor()).isNull();
    assertThat(report.dslDescriptor()).isNull();
    assertThat(report.astTree()).isNull();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void diagramFieldsAreDistinctAndExposed() {
    var report = new ExplainReport(
            "n", "d", "mermaid-only", "plantuml-only", "bpmn-only",
            List.of(), List.of(), Map.of(), null, null, null, List.of(), null, List.of());

    assertThat(report.mermaidDiagram()).isEqualTo("mermaid-only");
    assertThat(report.plantUmlDiagram()).isEqualTo("plantuml-only");
    assertThat(report.bpmnXml()).isEqualTo("bpmn-only");
  }

  @Test
  void explainReportCarriesDistinctFieldsVersusPreviewReport() {
    var explain = new ExplainReport(
            "n", "shared description", "m", "p", "b",
            List.of(), List.of(), Map.of(), null, null, null, List.of(), null, List.of());

    assertThat(explain.description()).isEqualTo("shared description");
    assertThat(explain).extracting("description", "mermaidDiagram", "plantUmlDiagram", "bpmnXml")
            .containsExactly("shared description", "m", "p", "b");
  }

  @Test
  void equalsAndHashCodeBasedOnAllComponents() {
    var trace = List.of("step-1");
    var calls = List.<Map<String, Object>>of();
    var counts = Map.<String, Integer>of("a", 1);
    var left = new ExplainReport(
            "n", "d", "m", "p", "b", trace, calls, counts, null, null, null, List.of(), null,
            List.of());
    var right = new ExplainReport(
            "n", "d", "m", "p", "b", List.of("step-1"), List.of(), Map.of("a", 1), null,
            null, null, List.of(), null, List.of());

    assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);

    var differentMermaid = new ExplainReport(
            "n", "d", "other", "p", "b", trace, calls, counts, null, null, null,
            List.of(),
            null,
            List.of());
    assertThat(left).isNotEqualTo(differentMermaid);

    var executable = new ExecutableDescriptor(
            "e", null, null, null, false, null, List.of());
    var differentExecutable = new ExplainReport(
            "n", "d", "m", "p", "b", trace, calls, counts, executable, null, null,
            List.of(),
            null,
            List.of());
    assertThat(left).isNotEqualTo(differentExecutable);
  }

  @Test
  void toStringContainsComponentNames() {
    var report = new ExplainReport(
            "n", "d", "m", "p", "b", List.of(), List.of(), Map.of(), null, null, null,
            List.of(),
            null,
            List.of());

    String text = report.toString();
    assertThat(text)
            .contains("name", "description", "mermaidDiagram", "plantUmlDiagram", "bpmnXml",
                    "executionTrace", "externalCalls", "callCounts",
                    "executableDescriptor", "dslDescriptor",
                    "astTree", "dryRunLogs");
  }
}
