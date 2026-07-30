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
            counts,
            null,
            List.of(),
            null,
            List.of());

    assertThat(report.name()).isEqualTo("greet");
    assertThat(report.mode()).isEqualTo(ExecutionMode.PREVIEW);
    assertThat(report.success()).isTrue();
    assertThat(report.output()).isEqualTo("hello");
    assertThat(report.executionTrace()).containsExactly("step-1", "step-2");
    assertThat(report.externalCalls()).containsExactly(call);
    assertThat(report.callCounts()).containsExactly(Map.entry("log", 1));
    assertThat(report.astTree()).isNull();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
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
            Map.of(),
            null,
            List.of(),
            null,
            List.of());

    assertThat(report.output()).isNull();
    assertThat(report.success()).isFalse();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void modeExposesExecutionModeEnum() {
    var preview = new PreviewReport(
            "p", ExecutionMode.PREVIEW, true, null, List.of(), List.of(), Map.of(), null,
            List.of(), null, List.of());

    assertThat(preview.mode()).isEqualTo(ExecutionMode.PREVIEW);
  }

  @Test
  void emptyTraceAndCallCollectionsAreReturned() {
    var report = new PreviewReport(
            "empty", ExecutionMode.PREVIEW, true, null, List.of(), List.of(), Map.of(), null,
            List.of(), null, List.of());

    assertThat(report.executionTrace()).isEmpty();
    assertThat(report.externalCalls()).isEmpty();
    assertThat(report.callCounts()).isEmpty();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void errorsFieldExposesPopulatedList() {
    var detail = new PreviewErrorDetail(PreviewErrorCode.HELPER_NOT_FOUND, "missing",
            "register the helper", Map.of("name", "Missing"));
    var report = new PreviewReport(
            "p", ExecutionMode.PREVIEW, false, null, List.of(), List.of(), Map.of(), null,
            List.of(), null, List.of(detail));

    assertThat(report.errors()).containsExactly(detail);
    assertThat(report.errors().get(0).code()).isEqualTo(PreviewErrorCode.HELPER_NOT_FOUND);
  }

  @Test
  void equalsAndHashCodeBasedOnAllComponents() {
    var trace = List.of("step-1");
    var calls = List.<Map<String, Object>>of();
    var counts = Map.<String, Integer>of("a", 1);
    var left = new PreviewReport(
            "n", ExecutionMode.PREVIEW, true, "out", trace, calls, counts, null, List.of(), null,
            List.of());
    var right = new PreviewReport(
            "n", ExecutionMode.PREVIEW, true, "out", List.of("step-1"), List.of(),
            Map.of("a", 1), null, List.of(), null, List.of());

    assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);

    var differentOutput = new PreviewReport(
            "n", ExecutionMode.PREVIEW, true, "other", trace, calls, counts, null, List.of(), null,
            List.of());
    assertThat(left).isNotEqualTo(differentOutput);

    var differentMode = new PreviewReport(
            "n", ExecutionMode.EXPLAIN, true, "out", trace, calls, counts, null, List.of(), null,
            List.of());
    assertThat(left).isNotEqualTo(differentMode);

    var differentSuccess = new PreviewReport(
            "n", ExecutionMode.PREVIEW, false, "out", trace, calls, counts, null, List.of(), null,
            List.of());
    assertThat(left).isNotEqualTo(differentSuccess);
  }

  @Test
  void toStringContainsComponentNames() {
    var report = new PreviewReport(
            "n", ExecutionMode.PREVIEW, true, null, List.of(), List.of(), Map.of(), null,
            List.of(), null, List.of());

    String text = report.toString();
    assertThat(text)
            .contains("name", "mode", "success", "output",
                    "executionTrace", "externalCalls", "callCounts",
                    "astTree", "dryRunLogs");
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
            null,
            null,
            List.of(),
            null,
            List.of());

    var preview = new PreviewReport(
            "n",
            ExecutionMode.PREVIEW,
            true,
            null,
            List.of("step-1"),
            List.of(),
            Map.of(),
            null,
            List.of(),
            null,
            List.of());

    assertThat(preview).isNotEqualTo(explain);
  }
}
