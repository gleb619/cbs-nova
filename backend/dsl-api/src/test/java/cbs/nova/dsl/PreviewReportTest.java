package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PreviewReportTest {

  @Test
  void accessorsExposeAllComponents() {
    Map<String, Object> call = Map.of("name", "log", "args", List.of("hi"));
    Map<String, Integer> counts = Map.of("log", 1);
    var report = new PreviewReport(
            "greet",
            ExecutionMode.PREVIEW,
            true,
            "hello",
            List.of("step-1", "step-2"),
            List.of(call),
            counts);

    assertThat(report.name()).isEqualTo("greet");
    assertThat(report.mode()).isEqualTo(ExecutionMode.PREVIEW);
    assertThat(report.success()).isTrue();
    assertThat(report.output()).isEqualTo("hello");
    assertThat(report.executionTrace()).containsExactly("step-1", "step-2");
    assertThat(report.externalCalls()).containsExactly(call);
    assertThat(report.callCounts()).containsExactly(Map.entry("log", 1));
  }

  @Test
  void nullableOutputAcceptsNull() {
    var report = new PreviewReport(
            "fail",
            ExecutionMode.PREVIEW,
            false,
            null,
            List.of(),
            List.of(),
            Map.of());

    assertThat(report.output()).isNull();
    assertThat(report.success()).isFalse();
  }

  @Test
  void modeExposesExecutionModeEnum() {
    var preview = new PreviewReport(
            "p", ExecutionMode.PREVIEW, true, null, List.of(), List.of(), Map.of());

    assertThat(preview.mode()).isEqualTo(ExecutionMode.PREVIEW);
  }

  @Test
  void emptyTraceAndCallCollectionsAreReturned() {
    var report = new PreviewReport(
            "empty", ExecutionMode.PREVIEW, true, null, List.of(), List.of(), Map.of());

    assertThat(report.executionTrace()).isEmpty();
    assertThat(report.externalCalls()).isEmpty();
    assertThat(report.callCounts()).isEmpty();
  }

  @Test
  void equalsAndHashCodeBasedOnAllComponents() {
    var trace = List.of("step-1");
    var calls = List.<Map<String, Object>>of();
    var counts = Map.<String, Integer>of("a", 1);
    var left = new PreviewReport(
            "n", ExecutionMode.PREVIEW, true, "out", trace, calls, counts);
    var right = new PreviewReport(
            "n", ExecutionMode.PREVIEW, true, "out", List.of("step-1"), List.of(), Map.of("a", 1));

    assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);

    var differentOutput = new PreviewReport(
            "n", ExecutionMode.PREVIEW, true, "other", trace, calls, counts);
    assertThat(left).isNotEqualTo(differentOutput);

    var differentMode = new PreviewReport(
            "n", ExecutionMode.EXPLAIN, true, "out", trace, calls, counts);
    assertThat(left).isNotEqualTo(differentMode);

    var differentSuccess = new PreviewReport(
            "n", ExecutionMode.PREVIEW, false, "out", trace, calls, counts);
    assertThat(left).isNotEqualTo(differentSuccess);
  }

  @Test
  void toStringContainsComponentNames() {
    var report = new PreviewReport(
            "n", ExecutionMode.PREVIEW, true, null, List.of(), List.of(), Map.of());

    String text = report.toString();
    assertThat(text)
            .contains("name", "mode", "success", "output",
                    "executionTrace", "externalCalls", "callCounts");
  }

  @Test
  void previewReportIsNotEqualToExplainReportWithSharedFields() {
    var explain = new ExplainReport(
            "n",
            "d",
            "mermaid",
            "plant",
            "bpmn",
            List.of("step-1"),
            List.of(),
            Map.of(),
            null,
            null);

    var preview = new PreviewReport(
            "n",
            ExecutionMode.PREVIEW,
            true,
            null,
            List.of("step-1"),
            List.of(),
            Map.of());

    assertThat(preview).isNotEqualTo(explain);
  }
}
