package cbs.nova.dsl.model;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExplainReportTest {

  @Test
  void mergeCombinesDescriptionsAndDiagrams() {
    var left = new ExplainReport("n", "left-desc", "left-diagram");
    var right = new ExplainReport("n", "right-desc", "right-diagram");
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.name()).isEqualTo("n");
    assertThat(merged.description()).contains("left-desc").contains("right-desc");
    assertThat(merged.mermaid()).contains("left-diagram").contains("right-diagram");
  }

  @Test
  void mergeIgnoresNonePlaceholderDescription() {
    var left = new ExplainReport("n", "left-desc", "left-diagram");
    var right = new ExplainReport("n", EMPTY_MARKDOWN, "right-diagram");
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.description()).isEqualTo("left-desc");
    assertThat(merged.mermaid()).contains("left-diagram").contains("right-diagram");
  }

  @Test
  void mergeKeepsSecondDescriptionWhenFirstIsNone() {
    var left = new ExplainReport("n", EMPTY_MARKDOWN, "left-diagram");
    var right = new ExplainReport("n", "right-desc", "right-diagram");
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.description()).isEqualTo("right-desc");
  }

  @Test
  void truncateToLeavesShortReportUnchanged() {
    var report = new ExplainReport("n", "abc", "def");
    assertThat(ExplainReports.truncateTo(report, 10)).isSameAs(report);
  }

  @Test
  void truncateToTruncatesDescriptionFirstThenDiagram() {
    var report = new ExplainReport("n", "description", "mermaidDiagram");
    var truncated = ExplainReports.truncateTo(report, 15);
    assertThat(truncated.description()).isEqualTo("description");
    assertThat(truncated.mermaid()).isEqualTo("merm");
  }

  @Test
  void truncateToEmptyDiagramWhenBudgetExhaustedByDescription() {
    var report = new ExplainReport("n", "description", "mermaidDiagram");
    var truncated = ExplainReports.truncateTo(report, 11);
    assertThat(truncated.description()).isEqualTo("description");
    assertThat(truncated.mermaid()).isEmpty();
  }

  @Test
  void truncateToHandlesNegativeBudget() {
    var report = new ExplainReport("n", "description", "mermaidDiagram");
    var truncated = ExplainReports.truncateTo(report, -1);
    assertThat(truncated.description()).isEmpty();
    assertThat(truncated.mermaid()).isEmpty();
  }

  @Test
  void truncateToHandlesZeroBudget() {
    var report = new ExplainReport("n", "description", "mermaidDiagram");
    var truncated = ExplainReports.truncateTo(report, 0);
    assertThat(truncated.description()).isEmpty();
    assertThat(truncated.mermaid()).isEmpty();
  }

  @Test
  void noArgChildrenDefaultsToEmpty() {
    var report = new ExplainReport("n", "desc", "");
    assertThat(report.children()).isEmpty();
  }

  @Test
  void addChildAppendsWithoutMutatingOriginal() {
    var parent = new ExplainReport("parent", "p-desc", "");
    var child = new ExplainReport("child", "c-desc", "");
    var withChild = parent.addChild(child);
    assertThat(parent.children()).isEmpty();
    assertThat(withChild.children()).containsExactly(child);
  }

  @Test
  void mergeCombinesChildrenByNameInsteadOfDuplicating() {
    var sharedLeft = new ExplainReport("shared", "left", "");
    var sharedRight = new ExplainReport("shared", "right", "");
    var left = new ExplainReport("n", "l", "").withChildren(List.of(sharedLeft));
    var right = new ExplainReport("n", "r", "").withChildren(List.of(sharedRight));
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.children()).hasSize(1);
    assertThat(merged.children().get(0).description()).contains("left").contains("right");
  }

  @Test
  void toMarkdownWalksGraphInCallOrderAndDedupesByName() {
    var helper = new ExplainReport("helper", "helper-desc", "graph TD\n  H");
    var shared = new ExplainReport("shared", "shared-desc", "").addChild(helper);
    var root = new ExplainReport("root", "root-desc", "")
            .withChildren(List.of(shared, shared));

    var markdown = ExplainReports.toMarkdown(root, 10_000);

    assertThat(markdown)
            .contains("## root")
            .contains("root-desc")
            .contains("## shared")
            .contains("## helper")
            .contains("```mermaid")
            .containsOnlyOnce("## shared");
  }

  @Test
  void toMarkdownStopsAtWholeNodeBoundaryAndReportsOmittedCount() {
    var child1 = new ExplainReport("child1", "d".repeat(20), "");
    var child2 = new ExplainReport("child2", "d".repeat(20), "");
    var root = new ExplainReport("root", "root-desc", "").withChildren(List.of(child1, child2));

    var rootSectionLength = "## root\n\nroot-desc".length();
    var markdown = ExplainReports.toMarkdown(root, rootSectionLength + 5);

    assertThat(markdown).contains("## root").doesNotContain("## child1")
            .doesNotContain("## child2");
    assertThat(markdown).contains("... 2 more nodes omitted, budget exhausted");
  }

  @Test
  void toMarkdownWithNoOmissionsHasNoTrailingMarker() {
    var report = new ExplainReport("n", "desc", "");
    assertThat(ExplainReports.toMarkdown(report, 1000)).doesNotContain("omitted");
  }
}
