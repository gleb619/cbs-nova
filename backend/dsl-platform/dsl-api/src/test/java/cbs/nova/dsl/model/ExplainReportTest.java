package cbs.nova.dsl.model;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExplainReportTest {

  @Test
  void mergeCombinesDescriptionsAndDiagrams() {
    var left = new ExplainReport("n", "left-desc", "left-diagram");
    var right = new ExplainReport("n", "right-desc", "right-diagram");
    var merged = left.merge(right);
    assertThat(merged.name()).isEqualTo("n");
    assertThat(merged.description()).contains("left-desc").contains("right-desc");
    assertThat(merged.mermaid()).contains("left-diagram").contains("right-diagram");
  }

  @Test
  void mergeIgnoresNonePlaceholderDescription() {
    var left = new ExplainReport("n", "left-desc", "left-diagram");
    var right = new ExplainReport("n", EMPTY_MARKDOWN, "right-diagram");
    var merged = left.merge(right);
    assertThat(merged.description()).isEqualTo("left-desc");
    assertThat(merged.mermaid()).contains("left-diagram").contains("right-diagram");
  }

  @Test
  void mergeKeepsSecondDescriptionWhenFirstIsNone() {
    var left = new ExplainReport("n", EMPTY_MARKDOWN, "left-diagram");
    var right = new ExplainReport("n", "right-desc", "right-diagram");
    var merged = left.merge(right);
    assertThat(merged.description()).isEqualTo("right-desc");
  }

  @Test
  void truncateToLeavesShortReportUnchanged() {
    var report = new ExplainReport("n", "abc", "def");
    assertThat(report.truncateTo(10)).isSameAs(report);
  }

  @Test
  void truncateToTruncatesDescriptionFirstThenDiagram() {
    var report = new ExplainReport("n", "description", "mermaidDiagram");
    var truncated = report.truncateTo(15);
    assertThat(truncated.description()).isEqualTo("description");
    assertThat(truncated.mermaid()).isEqualTo("merm");
  }

  @Test
  void truncateToEmptyDiagramWhenBudgetExhaustedByDescription() {
    var report = new ExplainReport("n", "description", "mermaidDiagram");
    var truncated = report.truncateTo(11);
    assertThat(truncated.description()).isEqualTo("description");
    assertThat(truncated.mermaid()).isEmpty();
  }

  @Test
  void truncateToHandlesNegativeBudget() {
    var report = new ExplainReport("n", "description", "mermaidDiagram");
    var truncated = report.truncateTo(-1);
    assertThat(truncated.description()).isEmpty();
    assertThat(truncated.mermaid()).isEmpty();
  }

  @Test
  void truncateToHandlesZeroBudget() {
    var report = new ExplainReport("n", "description", "mermaidDiagram");
    var truncated = report.truncateTo(0);
    assertThat(truncated.description()).isEmpty();
    assertThat(truncated.mermaid()).isEmpty();
  }
}
