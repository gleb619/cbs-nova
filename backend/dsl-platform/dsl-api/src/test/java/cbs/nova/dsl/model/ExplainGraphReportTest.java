package cbs.nova.dsl.model;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ExecutableDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExplainGraphReportTest {

  private static final List<Map<String, Object>> CALLS = List.of(
          Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"));
  private static final Map<String, Integer> COUNTS = Map.of("http", 2);

  @Test
  void accessorsExposeAllComponents() {
    Map<String, Object> call = Map.of("name", "log");
    Map<String, Integer> counts = Map.of("log", 2);
    var executable = new ExecutableDescriptor(
            "echo", "Echoes", String.class, String.class, false, null, List.of());
    var dsl = descriptor("echo", DslObject.DslType.PROCESS);
    var ast = new CallNode("echo", CallKind.PROCESS, null, null, true, List.of(), List.of());

    var report = new ExplainGraphReport(
            "echo",
            "Echoes input",
            List.of("step-1"),
            List.of(call),
            counts,
            true,
            executable,
            dsl,
            ast,
            List.of(),
            null,
            List.of(),
            List.of(),
            null);

    assertThat(report.name()).isEqualTo("echo");
    assertThat(report.description()).isEqualTo("Echoes input");
    assertThat(report.executionTrace()).containsExactly("step-1");
    assertThat(report.externalCalls()).containsExactly(call);
    assertThat(report.callCounts()).containsExactly(Map.entry("log", 2));
    assertThat(report.hasCompensation()).isTrue();
    assertThat(report.executableDescriptor()).isSameAs(executable);
    assertThat(report.dslDescriptor()).isSameAs(dsl);
    assertThat(report.astTree()).isSameAs(ast);
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
    assertThat(report.children()).isEmpty();
    assertThat(report.mermaidDiagram()).isNull();
  }

  @Test
  void nullableDescriptorsAcceptNull() {
    var report = new ExplainGraphReport(
            "n", "d",
            List.of(), List.of(), Map.of(),
            false, null, null, null, List.of(), null, List.of(), List.of(), null);

    assertThat(report.executableDescriptor()).isNull();
    assertThat(report.dslDescriptor()).isNull();
    assertThat(report.astTree()).isNull();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
    assertThat(report.children()).isEmpty();
  }

  @Test
  void listComponentsAreCopiedFromMutableSources() {
    var dryRunLogs = new ArrayList<Map<String, Object>>();
    var errors = new ArrayList<ErrorResponse>();
    var children = new ArrayList<ExplainGraphReport>();
    var report = new ExplainGraphReport(
            "n", "d", List.of(), List.of(), Map.of(),
            false, null, null, null, dryRunLogs, null, errors, children, null);
    dryRunLogs.add(Map.of("log", "entry"));
    errors.add(null);
    children.add(report);

    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.errors()).isEmpty();
    assertThat(report.children()).isEmpty();
  }

  @Test
  void equalsAndHashCodeBasedOnAllComponents() {
    var trace = List.of("step-1");
    var calls = List.<Map<String, Object>>of();
    var counts = Map.of("a", 1);
    var left = new ExplainGraphReport(
            "n", "d", trace, calls, counts, false, null, null, null, List.of(), null, List.of(),
            List.of(), null);
    var right = new ExplainGraphReport(
            "n", "d", List.of("step-1"), List.of(), Map.of("a", 1), false, null, null, null,
            List.of(), null, List.of(), List.of(), null);

    assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);

    var differentDescription = new ExplainGraphReport(
            "n", "other", trace, calls, counts, false, null, null, null, List.of(), null, List.of(),
            List.of(), null);
    assertThat(left).isNotEqualTo(differentDescription);

    var executable = new ExecutableDescriptor(
            "e", null, null, null, false, null, List.of());
    var differentExecutable = new ExplainGraphReport(
            "n", "d", trace, calls, counts, false, executable, null, null, List.of(), null,
            List.of(), List.of(), null);
    assertThat(left).isNotEqualTo(differentExecutable);

    var withCompensation = new ExplainGraphReport(
            "n", "d", trace, calls, counts, true, null, null, null, List.of(), null, List.of(),
            List.of(), null);
    assertThat(left).isNotEqualTo(withCompensation);

    var withChild = new ExplainGraphReport(
            "n", "d", trace, calls, counts, false, null, null, null, List.of(), null, List.of(),
            List.of(left), null);
    assertThat(left).isNotEqualTo(withChild);
  }

  @Test
  void toStringContainsComponentNames() {
    var report = new ExplainGraphReport(
            "n", "d", List.of(), List.of(), Map.of(), false, null, null, null, List.of(), null,
            List.of(), List.of(), null);

    String text = report.toString();
    assertThat(text)
            .contains("name", "description", "executionTrace", "externalCalls", "callCounts",
                    "hasCompensation", "executableDescriptor", "dslDescriptor", "astTree",
                    "dryRunLogs", "children", "mermaidDiagram");
  }

  @Test
  void toMermaidRendersProcessNodeFromReportFieldsOnly() {
    var report = graphReport("LoanDisbursement", DslObject.DslType.PROCESS, true);

    assertThat(report.toMermaid()).isEqualTo("""
            graph TD
              Start([Start]) --> Execute[LoanDisbursement]
              Execute --> |POST /pay| http0[HTTP: payment-api]
              Execute --> |success| End([End])
              Execute --> |failure| Compensate[Compensate]
              Compensate --> End
              %% Call Counts: http: 2""");
  }

  @Test
  void toMermaidRendersTransactionAndHelperNodes() {
    var tx = graphReport("KycCheck", DslObject.DslType.TRANSACTION, false);
    var helper = graphReport("MyHelper", DslObject.DslType.OTHER, false);

    assertThat(tx.toMermaid()).isEqualTo("""
            graph TD
              Start([Start]) --> Activity[KycCheck]
              Activity --> |POST /pay| http0[HTTP: payment-api]
              Activity --> |success| End([End])
              Activity --> |failure| Fail([Fail])
              %% Call Counts: http: 2""");
    assertThat(helper.toMermaid()).isEqualTo("""
            graph TD
              Start([Start]) --> Helper[MyHelper]
              Helper --> |POST /pay| http0[HTTP: payment-api]
              Helper --> End([End])
              %% Call Counts: http: 2""");
  }

  @Test
  void toPlantUmlRendersProcessNodeFromReportFieldsOnly() {
    var report = graphReport("LoanDisbursement", DslObject.DslType.PROCESS, true);

    assertThat(report.toPlantUml()).isEqualTo("""
            @startuml
            start
            :LoanDisbursement;
            :HTTP POST /pay (payment-api);
            if (success?) then (yes)
            else (no)
              :Compensate;
            endif
            stop

            ' Call Counts: http: 2@enduml""");
  }

  @Test
  void toBpmnRendersProcessNodeFromReportFieldsOnly() {
    var report = graphReport("LoanDisbursement", DslObject.DslType.PROCESS, true);

    assertThat(report.toBpmn()).isEqualTo(
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                      xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                      xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                      xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                                      id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
                      <bpmn:process id="Process_1" isExecutable="true">
                        <bpmn:startEvent id="StartEvent_1" name="Start">
                          <bpmn:outgoing>Flow_1</bpmn:outgoing>
                        </bpmn:startEvent>
                        <bpmn:serviceTask id="Activity_1" name="HTTP POST /pay (payment-api)">
                          <bpmn:incoming>Flow_1</bpmn:incoming>
                          <bpmn:outgoing>Flow_2</bpmn:outgoing>
                        </bpmn:serviceTask>
                        <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1" />
                        <bpmn:serviceTask id="Activity_2" name="LoanDisbursement">
                          <bpmn:incoming>Flow_3</bpmn:incoming>
                          <bpmn:outgoing>Flow_4</bpmn:outgoing>
                        </bpmn:serviceTask>
                        <bpmn:sequenceFlow id="Flow_3" sourceRef="Activity_1" targetRef="Activity_2" />
                        <bpmn:exclusiveGateway id="Gateway_1" name="Success?">
                          <bpmn:incoming>Flow_4</bpmn:incoming>
                          <bpmn:outgoing>Flow_Success</bpmn:outgoing>
                          <bpmn:outgoing>Flow_Fail</bpmn:outgoing>
                        </bpmn:exclusiveGateway>
                        <bpmn:sequenceFlow id="Flow_4" sourceRef="Activity_2" targetRef="Gateway_1" />
                        <bpmn:serviceTask id="Activity_Compensate" name="Compensate">
                          <bpmn:incoming>Flow_Fail</bpmn:incoming>
                          <bpmn:outgoing>Flow_Comp_End</bpmn:outgoing>
                        </bpmn:serviceTask>
                        <bpmn:endEvent id="EndEvent_1" name="End">
                          <bpmn:incoming>Flow_Success</bpmn:incoming>
                          <bpmn:incoming>Flow_Comp_End</bpmn:incoming>
                        </bpmn:endEvent>
                        <bpmn:sequenceFlow id="Flow_Success" name="Yes" sourceRef="Gateway_1" targetRef="EndEvent_1" />
                        <bpmn:sequenceFlow id="Flow_Comp_End" sourceRef="Activity_Compensate" targetRef="EndEvent_1" />
                      </bpmn:process>
                      <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                        <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
                          <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
                            <dc:Bounds x="173" y="102" width="36" height="36" />
                          </bpmndi:BPMNShape>
                        </bpmndi:BPMNPlane>
                      </bpmndi:BPMNDiagram>

                      <!-- Call Counts: http: 2 --></bpmn:definitions>""");
  }

  @Test
  void childrenAreRenderedInBreadthFirstOrderAfterTheRoot() {
    var childB = graphReport("ChildB", DslObject.DslType.TRANSACTION, false);
    var childC = graphReport("ChildC", DslObject.DslType.OTHER, false);
    var parent = graphReport("Parent", DslObject.DslType.PROCESS, true, childB, childC);

    String expected = nodeMermaid("Parent", DslObject.DslType.PROCESS, true)
            + "\n\n" + nodeMermaid("ChildB", DslObject.DslType.TRANSACTION, false)
            + "\n\n" + nodeMermaid("ChildC", DslObject.DslType.OTHER, false);
    assertThat(parent.toMermaid()).isEqualTo(expected);
    assertThat(parent.toPlantUml()).contains(":Parent;", ":ChildB;", ":ChildC;");
    assertThat(parent.toBpmn()).contains("name=\"Parent\"", "name=\"ChildB\"", "name=\"ChildC\"");
  }

  @Test
  void traversalIsCycleSafeAndVisitsEachNodeOnce() {
    var parentShell = graphReport("Parent", DslObject.DslType.PROCESS, true);
    var child = graphReport("Child", DslObject.DslType.TRANSACTION, false, parentShell);
    var parent = graphReport("Parent", DslObject.DslType.PROCESS, true, child);

    assertThat(parent.toMermaid().split("graph TD", -1)).hasSize(3);
    assertThat(parent.toPlantUml().split("@startuml", -1)).hasSize(3);
    assertThat(parent.toBpmn().split("<bpmn:definitions", -1)).hasSize(3);
  }

  private static String nodeMermaid(String name, DslObject.DslType kind, boolean hasCompensation) {
    return ExplainGraphDiagrams.mermaidNode(kind, name, hasCompensation, CALLS, COUNTS);
  }

  private static ExplainGraphReport graphReport(String name, DslObject.DslType kind,
          boolean hasCompensation, ExplainGraphReport... children) {
    return new ExplainGraphReport(
            name,
            name + " description",
            List.of(),
            CALLS,
            COUNTS,
            hasCompensation,
            null,
            descriptor(name, kind),
            null,
            List.of(),
            null,
            List.of(),
            List.of(children),
            null);
  }

  private static DslDescriptor descriptor(String name, DslObject.DslType type) {
    return DslDescriptor.builder()
            .objectDescriptor(new ObjectDescriptor() {
              @Override
              public String name() {
                return name;
              }

              @Override
              public DslObject.DslType type() {
                return type;
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
            .hasSideEffects(false)
            .parameters(List.of())
            .taskQueue(null)
            .version(null)
            .startToCloseTimeout(null)
            .heartbeatTimeout(null)
            .build();
  }
}
