package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.starter.core.pipe.ExplainBudget;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplainBudgetTest {

  private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
          .getEncoding(EncodingType.CL100K_BASE);

  private ExplainReport reportWith(String description, String markdown) {
    return reportWith("Test", description, markdown, List.of());
  }

  private ExplainReport reportWith(String name, String description, String markdown) {
    return reportWith(name, description, markdown, List.of());
  }

  private ExplainReport reportWith(String name, String description, String markdown,
          List<ExplainReport> children) {
    return new ExplainReport(name, description, markdown, children);
  }

  @Test
  void leavesReportUnchangedWhenWithinBudget() {
    var report = reportWith("short description", "graph TD; A-->B");

    ExplainReport bounded = ExplainBudget.apply(report, 100, 128, 256, 4096);

    assertThat(bounded.description()).isEqualTo("short description");
    assertThat(bounded.markdown()).isEqualTo("graph TD; A-->B");
  }

  @Test
  void truncatesDescriptionFirstThenDiagram() {
    var report = reportWith("this is a long description", "graph TD; A-->B");

    ExplainReport bounded = ExplainBudget.apply(report, 20, 128, 256, 4096);

    assertThat(bounded.description()).isEqualTo("this is a long descr");
    assertThat(bounded.markdown()).isEmpty();
  }

  @Test
  void returnsEmptyDiagramWhenBudgetExhaustedByDescription() {
    var report = reportWith("description", "mermaidDiagram");

    ExplainReport bounded = ExplainBudget.apply(report, 10, 128, 256, 4096);

    assertThat(bounded.description()).isEqualTo("descriptio");
    assertThat(bounded.markdown()).isEmpty();
  }

  @Test
  void handlesZeroBudget() {
    var report = reportWith("description", "mermaidDiagram");

    ExplainReport bounded = ExplainBudget.apply(report, 0, 128, 256, 4096);

    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.markdown()).isEmpty();
  }

  @Test
  void handlesNegativeBudgetAsZero() {
    var report = reportWith("description", "mermaidDiagram");

    ExplainReport bounded = ExplainBudget.apply(report, -5, 128, 256, 4096);

    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.markdown()).isEmpty();
  }

  @Test
  void preservesNameAndChildren() {
    var child = reportWith("Child", "child description", "graph TD; one --> two");
    var report = reportWith("Test", "description", "mermaidDiagram", List.of(child));

    ExplainReport bounded = ExplainBudget.apply(report, 1000, 128, 256, 4096);

    assertThat(bounded.name()).isEqualTo("Test");
    assertThat(bounded.children()).hasSize(1);
    assertThat(bounded.children().get(0).name()).isEqualTo("Child");
  }

  @Test
  void clampsOverLimitFieldsToConfiguredTokenCaps() {
    var report = reportWith(
            "alpha beta gamma delta epsilon zeta eta theta",
            "one two three four five six seven eight nine ten eleven twelve thirteen fourteen",
            "graph TD; alpha --> beta; gamma --> delta; epsilon --> zeta");

    ExplainReport bounded = ExplainBudget.apply(report, 100_000, 5, 10, 8);

    assertThat(ENCODING.countTokens(bounded.name())).isLessThanOrEqualTo(5);
    assertThat(ENCODING.countTokens(bounded.description())).isLessThanOrEqualTo(10);
    assertThat(ENCODING.countTokens(bounded.markdown())).isLessThanOrEqualTo(8);
    assertThat(bounded.name()).isNotEqualTo(report.name());
    assertThat(bounded.description()).isNotEqualTo(report.description());
    assertThat(bounded.markdown()).isNotEqualTo(report.markdown());
  }

  @Test
  void leavesUnderLimitFieldsUnchangedWithTokenCaps() {
    var report = reportWith("short name", "short description", "graph TD; A-->B");

    ExplainReport bounded = ExplainBudget.apply(report, 100_000, 128, 256, 4096);

    assertThat(bounded.name()).isEqualTo("short name");
    assertThat(bounded.description()).isEqualTo("short description");
    assertThat(bounded.markdown()).isEqualTo("graph TD; A-->B");
  }

  @Test
  void zeroTokenCapsClampFieldsToEmpty() {
    var report = reportWith("name", "description", "graph TD; A-->B");

    ExplainReport bounded = ExplainBudget.apply(report, 100_000, 0, 0, 0);

    assertThat(bounded.name()).isEmpty();
    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.markdown()).isEmpty();
  }

  @Test
  void negativeTokenCapsClampFieldsToEmpty() {
    var report = reportWith("name", "description", "graph TD; A-->B");

    ExplainReport bounded = ExplainBudget.apply(report, 100_000, -3, -1, -7);

    assertThat(bounded.name()).isEmpty();
    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.markdown()).isEmpty();
  }

  @Test
  void clampsChildNodeFieldsDuringSameTraversal() {
    var child = reportWith("Child",
            "alpha beta gamma delta epsilon zeta eta theta iota kappa",
            "graph TD; one --> two",
            List.of());
    var report = reportWith("Test", "short description", "graph TD; A-->B", List.of(child));

    ExplainReport bounded = ExplainBudget.apply(report, 100_000, 128, 5, 4096);

    assertThat(bounded.description()).isEqualTo("short description");
    assertThat(bounded.children()).hasSize(1);
    ExplainReport clampedChild = bounded.children().get(0);
    assertThat(ENCODING.countTokens(clampedChild.description())).isLessThanOrEqualTo(5);
    assertThat(clampedChild.description()).isNotEqualTo(child.description());
    assertThat(clampedChild.markdown()).isEqualTo("graph TD; one --> two");
  }
}
