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
    var ast = new CallNode("echo", CallKind.PROCESS, null, null, true, List.of(), List.of());

    var report = new ExplainReport(
            "echo",
            "Echoes input",
            List.of("step-1"),
            List.of(call),
            counts,
            executable,
            dsl,
            ast,
            List.of(),
            null,
            List.of(),
            null);

    assertThat(report.name()).isEqualTo("echo");
    assertThat(report.description()).isEqualTo("Echoes input");
    assertThat(report.executionTrace()).containsExactly("step-1");
    assertThat(report.externalCalls()).containsExactly(call);
    assertThat(report.callCounts()).containsExactly(Map.entry("log", 2));
    assertThat(report.executableDescriptor()).isSameAs(executable);
    assertThat(report.dslDescriptor()).isSameAs(dsl);
    assertThat(report.astTree()).isSameAs(ast);
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void nullableDescriptorsAcceptNull() {
    var report = new ExplainReport(
            "n", "d",
            List.of(), List.of(), Map.of(),
            null, null, null, List.of(), null, List.of(), null);

    assertThat(report.executableDescriptor()).isNull();
    assertThat(report.dslDescriptor()).isNull();
    assertThat(report.astTree()).isNull();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void equalsAndHashCodeBasedOnAllComponents() {
    var trace = List.of("step-1");
    var calls = List.<Map<String, Object>>of();
    var counts = Map.of("a", 1);
    var left = new ExplainReport(
            "n", "d", trace, calls, counts, null, null, null, List.of(), null, List.of(), null);
    var right = new ExplainReport(
            "n", "d", List.of("step-1"), List.of(), Map.of("a", 1), null, null, null,
            List.of(), null, List.of(), null);

    assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);

    var differentDescription = new ExplainReport(
            "n", "other", trace, calls, counts, null, null, null, List.of(), null, List.of(), null);
    assertThat(left).isNotEqualTo(differentDescription);

    var executable = new ExecutableDescriptor(
            "e", null, null, null, false, null, List.of());
    var differentExecutable = new ExplainReport(
            "n", "d", trace, calls, counts, executable, null, null, List.of(), null, List.of(),
            null);
    assertThat(left).isNotEqualTo(differentExecutable);
  }

  @Test
  void toStringContainsComponentNames() {
    var report = new ExplainReport(
            "n", "d", List.of(), List.of(), Map.of(), null, null, null, List.of(), null,
            List.of(), null);

    String text = report.toString();
    assertThat(text)
            .contains("name", "description", "executionTrace", "externalCalls", "callCounts",
                    "executableDescriptor", "dslDescriptor", "astTree", "dryRunLogs");
  }
}
