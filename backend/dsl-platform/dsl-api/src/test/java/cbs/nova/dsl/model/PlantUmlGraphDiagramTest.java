package cbs.nova.dsl.model;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslObject.DslType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Characterization spec for {@link PlantUmlGraphDiagram}. Pins @startuml/@enduml framing,
 * start/stop statements, activity and call-directive syntax, compensation if/else blocks,
 * call-count comments, and raw (unescaped) label emission. NOTE: no escaping is performed —
 * plantuml-breaking characters (e.g. {@code ;}) pass through verbatim. That gap is documented in
 * docs/plans/T690-diagram-builder-specs.md, not fixed here.
 */
class PlantUmlGraphDiagramTest {

  private static final Map<String, Object> HTTP_CALL = Map.of("type", "http", "target",
          "payment-api", "operation", "POST /pay");

  @Test
  void processFramingAndStatementsPinned() {
    String uml = PlantUmlGraphDiagram.render(DslType.PROCESS, "LoanDisbursement", true,
            List.of(HTTP_CALL), Map.of("http", 2));

    assertThat(uml).startsWith("@startuml\nstart\n:LoanDisbursement;");
    // Call directive: upper-cased type, operation, parenthesized target, semicolon.
    assertThat(uml).contains(":HTTP POST /pay (payment-api);");
    assertThat(uml).contains(
            "if (success?) then (yes)\nelse (no)\n  :Compensate;\nendif\n");
    assertThat(uml).endsWith("\n' Call Counts: http: 2@enduml");
    assertThat(uml).contains("stop\n");
  }

  @Test
  void transactionRendersSameActivityDiagramAsProcess() {
    String uml = PlantUmlGraphDiagram.render(DslType.TRANSACTION, "KycCheck", false,
            List.of(HTTP_CALL), null);

    assertThat(uml).contains(
            "@startuml\nstart\n:KycCheck;",
            ":HTTP POST /pay (payment-api);");
    assertThat(uml).doesNotContain("if (success?)", "Compensate", "Call Counts");
    assertThat(uml).endsWith("stop\n@enduml");
  }

  @Test
  void nonProcessKindsRenderHelperDiagramWithoutCompensation() {
    for (DslType kind : List.of(DslType.FUNCTION, DslType.OTHER)) {
      String uml = PlantUmlGraphDiagram.render(kind, "Helper", true, null, null);

      // Compensation flag is ignored for helper kinds.
      assertThat(uml).isEqualTo("@startuml\nstart\n:Helper;\nstop\n@enduml");
    }
  }

  @Test
  void callMapsFallBackToDefaultTypeTargetOperation() {
    String uml = PlantUmlGraphDiagram.render(DslType.PROCESS, "Defaults", false,
            List.of(Map.of()), null);

    assertThat(uml).contains(":EXTERNAL call (unknown);");
  }

  @Test
  void longCallTargetTruncatedToThirtyChars() {
    String longTarget = "a".repeat(35);
    String uml = PlantUmlGraphDiagram.render(DslType.PROCESS, "T", false,
            List.of(Map.of("type", "http", "target", longTarget, "operation", "GET")), null);

    assertThat(uml).contains(":HTTP GET (" + "a".repeat(27) + "...);");
    assertThat(uml).doesNotContain(longTarget);
  }

  @Test
  void callTargetExactlyThirtyCharsIsNotTruncated() {
    String target = "b".repeat(30);
    String uml = PlantUmlGraphDiagram.render(DslType.PROCESS, "T", false,
            List.of(Map.of("target", target)), null);

    assertThat(uml).contains(":EXTERNAL call (" + target + ");");
  }

  @Test
  void callCountsCommentIsSortedByKey() {
    String uml = PlantUmlGraphDiagram.render(DslType.PROCESS, "Counts", false, null,
            Map.of("zeta", 1, "alpha", 3, "mid", 2));

    assertThat(uml).endsWith("' Call Counts: alpha: 3, mid: 2, zeta: 1@enduml");
  }

  @Test
  void emptyCallsAndCountsEmitBareDiagram() {
    String uml = PlantUmlGraphDiagram.render(DslType.PROCESS, "Solo", false, List.of(),
            Map.of());

    assertThat(uml).isEqualTo("@startuml\nstart\n:Solo;\nstop\n@enduml");
  }

  @Test
  void plantUmlBreakingCharactersInNamePassThroughRaw() {
    String uml = PlantUmlGraphDiagram.render(DslType.PROCESS, "Name;stop\n:x;", false,
            List.of(Map.of("operation", "op;t")), null);

    // Characterization of current behavior: values are interpolated verbatim, no escaping.
    assertThat(uml).contains(":Name;stop\n:x;;", ":EXTERNAL op;t (unknown);");
  }
}
