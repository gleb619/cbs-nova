package cbs.nova.dsl.model;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExplainReportTest {

  @Test
  void mergeCombinesDescriptionsAndDiagrams() {
    var left = ExplainReport.builder().name("n").description("left-desc").mermaid("left-diagram")
            .build();
    var right = ExplainReport.builder().name("n").description("right-desc").mermaid("right-diagram")
            .build();
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.name()).isEqualTo("n");
    assertThat(merged.description()).contains("left-desc").contains("right-desc");
    assertThat(merged.mermaid()).contains("left-diagram").contains("right-diagram");
  }

  @Test
  void mergeIgnoresNonePlaceholderDescription() {
    var left = ExplainReport.builder().name("n").description("left-desc").mermaid("left-diagram")
            .build();
    var right = ExplainReport.builder().name("n").description(EMPTY_MARKDOWN)
            .mermaid("right-diagram").build();
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.description()).isEqualTo("left-desc");
    assertThat(merged.mermaid()).contains("left-diagram").contains("right-diagram");
  }

  @Test
  void mergeKeepsSecondDescriptionWhenFirstIsNone() {
    var left = ExplainReport.builder().name("n").description(EMPTY_MARKDOWN).mermaid("left-diagram")
            .build();
    var right = ExplainReport.builder().name("n").description("right-desc").mermaid("right-diagram")
            .build();
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.description()).isEqualTo("right-desc");
  }

  @Test
  void truncateToLeavesShortReportUnchanged() {
    var report = ExplainReport.builder().name("n").description("abc").mermaid("def").build();
    assertThat(ExplainReports.truncateTo(report, 10)).isSameAs(report);
  }

  @Test
  void truncateToTruncatesDescriptionFirstThenDiagram() {
    var report = ExplainReport.builder().name("n").description("description")
            .mermaid("mermaidDiagram").build();
    var truncated = ExplainReports.truncateTo(report, 15);
    assertThat(truncated.description()).isEqualTo("description");
    assertThat(truncated.mermaid()).isEqualTo("merm");
  }

  @Test
  void truncateToEmptyDiagramWhenBudgetExhaustedByDescription() {
    var report = ExplainReport.builder().name("n").description("description")
            .mermaid("mermaidDiagram").build();
    var truncated = ExplainReports.truncateTo(report, 11);
    assertThat(truncated.description()).isEqualTo("description");
    assertThat(truncated.mermaid()).isEmpty();
  }

  @Test
  void truncateToHandlesNegativeBudget() {
    var report = ExplainReport.builder().name("n").description("description")
            .mermaid("mermaidDiagram").build();
    var truncated = ExplainReports.truncateTo(report, -1);
    assertThat(truncated.description()).isEmpty();
    assertThat(truncated.mermaid()).isEmpty();
  }

  @Test
  void truncateToHandlesZeroBudget() {
    var report = ExplainReport.builder().name("n").description("description")
            .mermaid("mermaidDiagram").build();
    var truncated = ExplainReports.truncateTo(report, 0);
    assertThat(truncated.description()).isEmpty();
    assertThat(truncated.mermaid()).isEmpty();
  }

  @Test
  void noArgChildrenDefaultsToEmpty() {
    var report = ExplainReport.builder().name("n").description("desc").mermaid("").build();
    assertThat(report.children()).isEmpty();
  }

  @Test
  void addChildAppendsWithoutMutatingOriginal() {
    var parent = ExplainReport.builder().name("parent").description("p-desc").mermaid("").build();
    var child = ExplainReport.builder().name("child").description("c-desc").mermaid("").build();
    var withChild = parent.addChild(child);
    assertThat(parent.children()).isEmpty();
    assertThat(withChild.children()).containsExactly(child);
  }

  @Test
  void mergeCombinesChildrenByNameInsteadOfDuplicating() {
    var sharedLeft = ExplainReport.builder().name("shared").description("left").mermaid("").build();
    var sharedRight = ExplainReport.builder().name("shared").description("right").mermaid("")
            .build();
    var left = ExplainReport.builder().name("n").description("l").mermaid("").build()
            .withChildren(List.of(sharedLeft));
    var right = ExplainReport.builder().name("n").description("r").mermaid("").build()
            .withChildren(List.of(sharedRight));
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.children()).hasSize(1);
    assertThat(merged.children().get(0).description()).contains("left").contains("right");
  }

  @Test
  void toMarkdownWalksGraphInCallOrderAndDedupesByName() {
    var helper = ExplainReport.builder().name("helper").description("helper-desc")
            .mermaid("graph TD\n  H").build();
    var shared = ExplainReport.builder().name("shared").description("shared-desc").mermaid("")
            .build().addChild(helper);
    var root = ExplainReport.builder().name("root").description("root-desc").mermaid("").build()
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
    var child1 = ExplainReport.builder().name("child1").description("d".repeat(20)).mermaid("")
            .build();
    var child2 = ExplainReport.builder().name("child2").description("d".repeat(20)).mermaid("")
            .build();
    var root = ExplainReport.builder().name("root").description("root-desc").mermaid("").build()
            .withChildren(List.of(child1, child2));

    var rootSectionLength = "## root\n\nroot-desc".length();
    var markdown = ExplainReports.toMarkdown(root, rootSectionLength + 5);

    assertThat(markdown).contains("## root").doesNotContain("## child1")
            .doesNotContain("## child2");
    assertThat(markdown).contains("... 2 more nodes omitted, budget exhausted");
  }

  @Test
  void toMarkdownWithNoOmissionsHasNoTrailingMarker() {
    var report = ExplainReport.builder().name("n").description("desc").mermaid("").build();
    assertThat(ExplainReports.toMarkdown(report, 1000)).doesNotContain("omitted");
  }
}
