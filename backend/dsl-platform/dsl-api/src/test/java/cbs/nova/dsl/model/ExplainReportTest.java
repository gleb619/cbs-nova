package cbs.nova.dsl.model;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.utils.ExplainReports;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplainReportTest {

  @Test
  void mergeCombinesDescriptionsAndDiagrams() {
    var left = ExplainReport.builder().name("n").description("left-desc").markdown("left-diagram")
            .build();
    var right = ExplainReport.builder().name("n").description("right-desc")
            .markdown("right-diagram")
            .build();
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.name()).isEqualTo("n");
    assertThat(merged.description()).contains("left-desc").contains("right-desc");
    assertThat(merged.markdown()).contains("left-diagram").contains("right-diagram");
  }

  @Test
  void mergeIgnoresNonePlaceholderDescription() {
    var left = ExplainReport.builder().name("n").description("left-desc").markdown("left-diagram")
            .build();
    var right = ExplainReport.builder().name("n").description(EMPTY_MARKDOWN)
            .markdown("right-diagram").build();
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.description()).isEqualTo("left-desc");
    assertThat(merged.markdown()).contains("left-diagram").contains("right-diagram");
  }

  @Test
  void mergeKeepsSecondDescriptionWhenFirstIsNone() {
    var left = ExplainReport.builder().name("n").description(EMPTY_MARKDOWN)
            .markdown("left-diagram")
            .build();
    var right = ExplainReport.builder().name("n").description("right-desc")
            .markdown("right-diagram")
            .build();
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.description()).isEqualTo("right-desc");
  }

  @Test
  void noArgChildrenDefaultsToEmpty() {
    var report = ExplainReport.builder().name("n").description("desc").markdown("").build();
    assertThat(report.children()).isEmpty();
  }

  @Test
  void childrenListIsCopiedOnConstruction() {
    var parent = ExplainReport.builder().name("parent").description("p-desc").markdown("").build();
    var child = ExplainReport.builder().name("child").description("c-desc").markdown("").build();
    var children = new ArrayList<>(List.of(child));
    var withChild = ExplainReport.builder().name("parent").description("p-desc").markdown("")
            .children(children).build();
    children.clear();
    assertThat(parent.children()).isEmpty();
    assertThat(withChild.children()).containsExactly(child);
  }

  @Test
  void mergeCombinesChildrenByNameInsteadOfDuplicating() {
    var sharedLeft = ExplainReport.builder().name("shared").description("left").markdown("")
            .build();
    var sharedRight = ExplainReport.builder().name("shared").description("right").markdown("")
            .build();
    var left = ExplainReport.builder().name("n").description("l").markdown("")
            .children(List.of(sharedLeft)).build();
    var right = ExplainReport.builder().name("n").description("r").markdown("")
            .children(List.of(sharedRight)).build();
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.children()).hasSize(1);
    assertThat(merged.children().get(0).description()).contains("left").contains("right");
  }

  @Test
  void toMarkdownWalksGraphInCallOrderAndDedupesByName() {
    var helper = ExplainReport.builder().name("helper").description("helper-desc")
            .markdown("graph TD\n  H").build();
    var shared = ExplainReport.builder().name("shared").description("shared-desc").markdown("")
            .children(List.of(helper)).build();
    var root = ExplainReport.builder().name("root").description("root-desc").markdown("")
            .children(List.of(shared, shared)).build();

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
    var child1 = ExplainReport.builder().name("child1").description("d".repeat(20)).markdown("")
            .build();
    var child2 = ExplainReport.builder().name("child2").description("d".repeat(20)).markdown("")
            .build();
    var root = ExplainReport.builder().name("root").description("root-desc").markdown("")
            .children(List.of(child1, child2)).build();

    var rootSectionLength = "## root\n\nroot-desc".length();
    var markdown = ExplainReports.toMarkdown(root, rootSectionLength + 5);

    assertThat(markdown).contains("## root").doesNotContain("## child1")
            .doesNotContain("## child2");
    assertThat(markdown).contains("... 2 more nodes omitted, budget exhausted");
  }

  @Test
  void toMarkdownWithNoOmissionsHasNoTrailingMarker() {
    var report = ExplainReport.builder().name("n").description("desc").markdown("").build();
    assertThat(ExplainReports.toMarkdown(report, 1000)).doesNotContain("omitted");
  }
}
