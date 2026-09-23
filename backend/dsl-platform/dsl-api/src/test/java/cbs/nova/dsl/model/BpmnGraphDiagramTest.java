package cbs.nova.dsl.model;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Characterization spec for {@link BpmnGraphDiagram}. Pins current node ids, sequence-flow pairs,
 * labels, and call-count comment formatting so layout/builder regressions fail loudly.
 */
class BpmnGraphDiagramTest {

  private static final Map<String, Object> HTTP_CALL = Map.of("type", "http", "target",
          "payment-api", "operation", "POST /pay");

  @Test
  void processWithExternalCallAndCompensationPinsIdsEdgesAndLabels() {
    String xml = BpmnGraphDiagram.render(DslType.PROCESS, "LoanDisbursement", true,
            List.of(HTTP_CALL), Map.of("http", 2));

    // Activity chain: Start -> call activity -> main activity -> gateway.
    assertThat(xml).contains(
            "<bpmn:startEvent id=\"StartEvent_1\" name=\"Start\">",
            "<bpmn:serviceTask id=\"Activity_1\" name=\"HTTP POST /pay (payment-api)\">",
            "<bpmn:serviceTask id=\"Activity_2\" name=\"LoanDisbursement\">",
            "<bpmn:exclusiveGateway id=\"Gateway_1\" name=\"Success?\">",
            "<bpmn:serviceTask id=\"Activity_Compensate\" name=\"Compensate\">",
            "<bpmn:endEvent id=\"EndEvent_1\" name=\"End\">");

    // Sequence-flow edges (source -> target pairs).
    assertThat(xml).contains(
            "<bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"Activity_1\" />",
            "<bpmn:sequenceFlow id=\"Flow_3\" sourceRef=\"Activity_1\" targetRef=\"Activity_2\" />",
            "<bpmn:sequenceFlow id=\"Flow_4\" sourceRef=\"Activity_2\" targetRef=\"Gateway_1\" />",
            "<bpmn:sequenceFlow id=\"Flow_Success\" name=\"Yes\" sourceRef=\"Gateway_1\" targetRef=\"EndEvent_1\" />",
            "<bpmn:sequenceFlow id=\"Flow_Comp_End\" sourceRef=\"Activity_Compensate\" targetRef=\"EndEvent_1\" />");

    // Call counts pinned as sorted XML comment.
    assertThat(xml).contains("\n  <!-- Call Counts: http: 2 --></bpmn:definitions>");
  }

  @Test
  void transactionWithoutCompensationRendersSimpleEnd() {
    String xml = BpmnGraphDiagram.render(DslType.TRANSACTION, "KycCheck", false,
            null, null);

    assertThat(xml).contains(
            "<bpmn:serviceTask id=\"Activity_1\" name=\"KycCheck\">",
            "<bpmn:endEvent id=\"EndEvent_1\" name=\"End\">",
            "<bpmn:sequenceFlow id=\"Flow_2\" sourceRef=\"Activity_1\" targetRef=\"EndEvent_1\" />");
    assertThat(xml).doesNotContain("Gateway_", "Compensate", "Flow_Success", "Flow_Fail");
    assertThat(xml).doesNotContain("Call Counts");
  }

  @Test
  void singleNodeWithoutCallsOrCountsIsMinimalGraph() {
    String xml = BpmnGraphDiagram.render(DslType.PROCESS, "Solo", false, List.of(), Map.of());

    assertThat(xml).contains(
            "<bpmn:startEvent id=\"StartEvent_1\" name=\"Start\">",
            "<bpmn:serviceTask id=\"Activity_1\" name=\"Solo\">",
            "<bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"Activity_1\" />",
            "<bpmn:sequenceFlow id=\"Flow_2\" sourceRef=\"Activity_1\" targetRef=\"EndEvent_1\" />");
    assertThat(xml).doesNotContain("Gateway_", "Call Counts");
  }

  @Test
  void nonProcessKindsIgnoreCompensationFlag() {
    for (DslType kind : List.of(DslType.FUNCTION, DslType.OTHER)) {
      String xml = BpmnGraphDiagram.render(kind, "Helper", true, null, null);

      assertThat(xml).contains(
              "<bpmn:serviceTask id=\"Activity_1\" name=\"Helper\">",
              "<bpmn:sequenceFlow id=\"Flow_2\" sourceRef=\"Activity_1\" targetRef=\"EndEvent_1\" />");
      assertThat(xml).doesNotContain("Gateway_", "Compensate");
    }
  }

  @Test
  void duplicateExternalCallsEachGetOwnChainedActivity() {
    String xml = BpmnGraphDiagram.render(DslType.PROCESS, "Dup", false,
            List.of(HTTP_CALL, HTTP_CALL), null);

    assertThat(xml).contains(
            "<bpmn:serviceTask id=\"Activity_1\" name=\"HTTP POST /pay (payment-api)\">",
            "<bpmn:serviceTask id=\"Activity_2\" name=\"HTTP POST /pay (payment-api)\">",
            "<bpmn:serviceTask id=\"Activity_3\" name=\"Dup\">",
            "<bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"Activity_1\" />",
            "<bpmn:sequenceFlow id=\"Flow_3\" sourceRef=\"Activity_1\" targetRef=\"Activity_2\" />",
            "<bpmn:sequenceFlow id=\"Flow_5\" sourceRef=\"Activity_2\" targetRef=\"Activity_3\" />");
  }

  @Test
  void longCallTargetTruncatedToTwentyChars() {
    String longTarget = "a".repeat(25);
    String xml = BpmnGraphDiagram.render(DslType.PROCESS, "T", false,
            List.of(Map.of("type", "http", "target", longTarget, "operation", "GET")), null);

    assertThat(xml).contains("name=\"HTTP GET ("
            + "a".repeat(17) + "...)\">");
    assertThat(xml).doesNotContain(longTarget);
  }

  @Test
  void callMapsFallBackToDefaultTypeTargetOperation() {
    String xml = BpmnGraphDiagram.render(DslType.PROCESS, "Defaults", false,
            List.of(Map.of()), null);

    assertThat(xml)
            .contains("<bpmn:serviceTask id=\"Activity_1\" name=\"EXTERNAL call (unknown)\">");
  }

  @Test
  void callCountsCommentIsSortedByKey() {
    String xml = BpmnGraphDiagram.render(DslType.PROCESS, "Counts", false, null,
            Map.of("zeta", 1, "alpha", 3, "mid", 2));

    assertThat(xml).contains("<!-- Call Counts: alpha: 3, mid: 2, zeta: 1 -->");
  }

  @Test
  void hierarchyDiagramsBpmnMatchesPerNodeRenderInBreadthFirstOrder() {
    var childA = report("ChildA", DslType.TRANSACTION, false);
    var childB = report("ChildB", DslType.OTHER, false);
    var root = report("Root", DslType.PROCESS, true, childA, childB);

    String expected = HierarchyDiagrams.bpmnNode(DslType.PROCESS, "Root", true, List.of(), Map.of())
            + "\n\n" + HierarchyDiagrams.bpmnNode(DslType.TRANSACTION, "ChildA", false, List.of(),
                    Map.of())
            + "\n\n" + HierarchyDiagrams.bpmnNode(DslType.OTHER, "ChildB", false, List.of(),
                    Map.of());

    assertThat(HierarchyDiagrams.bpmn(root)).isEqualTo(expected);
  }

  private static HierarchyReport report(String name, DslType kind, boolean hasCompensation,
          HierarchyReport... children) {
    return new HierarchyReport(
            name,
            name + " description",
            List.of(),
            List.of(),
            Map.of(),
            hasCompensation,
            null,
            DslDescriptor.builder()
                    .objectDescriptor(new ObjectDescriptor() {
                      @Override
                      public String name() {
                        return name;
                      }

                      @Override
                      public DslType type() {
                        return kind;
                      }

                      @Override
                      public String description() {
                        return null;
                      }

                      @Override
                      public Class<?> inputType() {
                        return null;
                      }

                      @Override
                      public Class<?> outputType() {
                        return null;
                      }
                    })
                    .parameters(List.of())
                    .build(),
            null,
            List.of(),
            null,
            List.of(),
            List.of(children),
            null);
  }
}
