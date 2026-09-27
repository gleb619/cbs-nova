package cbs.nova.dsl.model;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslObject.DslType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Characterization spec for {@link MermaidGraphDiagram}. Pins graph TD header emission, node and
 * edge syntax, call-line rendering, call-count comments, and raw (unescaped) label emission. NOTE:
 * no escaping is performed — mermaid-breaking characters (e.g. {@code []{}|}) pass through
 * verbatim. That gap is documented in docs/plans/T690-diagram-builder-specs.md, not fixed here.
 */
class MermaidGraphDiagramTest {

  private static final Map<String, Object> HTTP_CALL = Map.of("type", "http", "target",
          "payment-api", "operation", "POST /pay");

  @Test
  void processWithCompensationPinsHeaderNodesEdgesAndCounts() {
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "LoanDisbursement", true,
            List.of(HTTP_CALL), Map.of("http", 2));

    assertThat(graph).startsWith("graph TD\n  Start([Start]) --> Execute[LoanDisbursement]");
    // External call line from the Execute node, zero-based type+index id and bracketed label.
    assertThat(graph).contains(
            "Execute --> |POST /pay| http0[HTTP: payment-api]");
    // Compensation branch: success to End, failure to Compensate, Compensate back to End.
    assertThat(graph).contains(
            "Execute --> |success| End([End])",
            "Execute --> |failure| Compensate[Compensate]",
            "Compensate --> End");
    assertThat(graph).doesNotContain("Fail([Fail])");
    // Call counts pinned as sorted mermaid comment.
    assertThat(graph).endsWith("\n  %% Call Counts: http: 2");
  }

  @Test
  void processWithoutCompensationRendersFailBranch() {
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "Solo", false, null, null);

    assertThat(graph).contains(
            "Start([Start]) --> Execute[Solo]",
            "Execute --> |success| End([End])",
            "Execute --> |failure| Fail([Fail])");
    assertThat(graph).doesNotContain("Compensate", "Call Counts");
    assertThat(graph).endsWith("Fail([Fail])");
  }

  @Test
  void transactionUsesActivityNodeAndActivityBranches() {
    String graph = MermaidGraphDiagram.render(DslType.TRANSACTION, "KycCheck", true,
            List.of(HTTP_CALL), null);

    assertThat(graph).contains(
            "Start([Start]) --> Activity[KycCheck]",
            "Activity --> |POST /pay| http0[HTTP: payment-api]",
            "Activity --> |success| End([End])",
            "Activity --> |failure| Compensate[Compensate]",
            "Compensate --> End");
    assertThat(graph).doesNotContain("Execute[", "Call Counts");
  }

  @Test
  void nonProcessKindsRenderStraightHelperToEnd() {
    for (DslType kind : List.of(DslType.FUNCTION, DslType.OTHER)) {
      String graph = MermaidGraphDiagram.render(kind, "Helper", true, null, null);

      // Compensation flag is ignored for helper kinds.
      assertThat(graph).contains(
              "graph TD\n  Start([Start]) --> Helper[Helper]\n  Helper --> End([End])");
      assertThat(graph).doesNotContain("Compensate", "Fail([Fail])", "Call Counts");
    }
  }

  @Test
  void helperKindEmitsExternalCallLinesFromHelperNode() {
    String graph = MermaidGraphDiagram.render(DslType.FUNCTION, "Helper", false,
            List.of(HTTP_CALL), null);

    assertThat(graph).contains(
            "Helper[Helper]\n  Helper --> |POST /pay| http0[HTTP: payment-api]\n  Helper --> End");
  }

  @Test
  void duplicateCallsEachGetOwnIndexedNode() {
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "Dup", false,
            List.of(HTTP_CALL, HTTP_CALL), null);

    assertThat(graph).contains(
            "http0[HTTP: payment-api]",
            "http1[HTTP: payment-api]");
  }

  @Test
  void callMapsFallBackToDefaultTypeTargetOperation() {
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "Defaults", false,
            List.of(Map.of()), null);

    assertThat(graph).contains("Execute --> |call| external0[EXTERNAL: unknown]");
  }

  @Test
  void longCallTargetTruncatedToTwentyChars() {
    String longTarget = "a".repeat(25);
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "T", false,
            List.of(Map.of("type", "http", "target", longTarget, "operation", "GET")), null);

    assertThat(graph).contains("http0[HTTP: " + "a".repeat(17) + "...]");
    assertThat(graph).doesNotContain(longTarget);
  }

  @Test
  void callTargetExactlyTwentyCharsIsNotTruncated() {
    String target = "b".repeat(20);
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "T", false,
            List.of(Map.of("target", target)), null);

    assertThat(graph).contains("external0[EXTERNAL: " + target + "]");
  }

  @Test
  void callCountsCommentIsSortedByKey() {
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "Counts", false, null,
            Map.of("zeta", 1, "alpha", 3, "mid", 2));

    assertThat(graph).endsWith("\n  %% Call Counts: alpha: 3, mid: 2, zeta: 1");
  }

  @Test
  void emptyCallsAndCountsEmitNoExtras() {
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "Solo", false, List.of(),
            Map.of());

    assertThat(graph).isEqualTo(
            "graph TD\n  Start([Start]) --> Execute[Solo]\n  Execute --> |success| End([End])\n"
                    + "  Execute --> |failure| Fail([Fail])");
  }

  @Test
  void mermaidBreakingCharactersInNameAndLabelsPassThroughRaw() {
    String graph = MermaidGraphDiagram.render(DslType.PROCESS, "Name[with]brackets", false,
            List.of(Map.of("target", "t[1]", "operation", "op|x")), null);

    // Characterization of current behavior: values are interpolated verbatim, no escaping.
    assertThat(graph).contains(
            "Execute[Name[with]brackets]",
            "external0[EXTERNAL: t[1]]",
            "Execute --> |op|x| external0");
  }
}
