package cbs.nova.dsl.generator;

import cbs.nova.dsl.DiagramGenerator;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.utils.Substitutor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class BpmnDiagramGenerator implements DiagramGenerator {

  private static String buildSimpleCompensation(boolean hasCompensation) {
    if (hasCompensation) {
      return """
              <bpmn:exclusiveGateway id="Gateway_1" name="Success?">
                <bpmn:incoming>Flow_2</bpmn:incoming>
                <bpmn:outgoing>Flow_Success</bpmn:outgoing>
                <bpmn:outgoing>Flow_Fail</bpmn:outgoing>
              </bpmn:exclusiveGateway>
              <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="Gateway_1" />
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
              """
              .indent(4);
    }
    return """
            <bpmn:endEvent id="EndEvent_1" name="End">
              <bpmn:incoming>Flow_2</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="EndEvent_1" />
            """.indent(4);
  }

  private static String buildBody(String name, boolean hasCompensation,
          @Nullable List<Map<String, Object>> externalCalls) {
    var ids = new Ids();
    var sb = new StringBuilder();

    String mainSourceRef = "StartEvent_1";

    if (externalCalls != null && !externalCalls.isEmpty()) {
      for (Map<String, Object> call : externalCalls) {
        int activityId = ids.nextActivity();
        int incomingFlow = ids.nextFlow();
        int outgoingFlow = ids.nextFlow();
        sb.append(buildActivityNode(activityId,
                buildCallName(call), incomingFlow, outgoingFlow));
        sb.append(buildSequenceFlow(incomingFlow, mainSourceRef, "Activity_" + activityId));
        mainSourceRef = "Activity_" + activityId;
      }
    }

    int mainActivityId = ids.nextActivity();
    int mainIncomingFlow = ids.nextFlow();
    int mainOutgoingFlow = ids.nextFlow();
    sb.append(buildActivityNode(mainActivityId, name, mainIncomingFlow, mainOutgoingFlow));
    sb.append(buildSequenceFlow(mainIncomingFlow, mainSourceRef, "Activity_" + mainActivityId));

    if (hasCompensation) {
      int gatewayId = ids.nextGateway();
      sb.append(buildCompensationGateway(gatewayId, mainOutgoingFlow, mainActivityId));
    } else {
      sb.append(buildSimpleEnd(mainOutgoingFlow, "Activity_" + mainActivityId));
    }

    return sb.toString();
  }

  private static String buildCallName(Map<String, Object> call) {
    String callType = (String) call.getOrDefault("type", "external");
    String callTarget = (String) call.getOrDefault("target", "unknown");
    String callOperation = (String) call.getOrDefault("operation", "call");
    String displayTarget = callTarget.length() > 20
            ? callTarget.substring(0, 17) + "..."
            : callTarget;
    return callType.toUpperCase() + " " + callOperation + " (" + displayTarget + ")";
  }

  private static String buildActivityNode(int activityId, String name, int incomingFlow,
          int outgoingFlow) {
    var template = """
            <bpmn:serviceTask id="Activity_${activityId}" name="${name}">
              <bpmn:incoming>Flow_${incoming}</bpmn:incoming>
              <bpmn:outgoing>Flow_${outgoing}</bpmn:outgoing>
            </bpmn:serviceTask>
            """.indent(4);
    return Substitutor.format(template, Map.of(
            "activityId", activityId,
            "name", name,
            "incoming", incomingFlow,
            "outgoing", outgoingFlow));
  }

  private static String buildSequenceFlow(int flowId, String sourceRef, String targetRef) {
    var template = """
            <bpmn:sequenceFlow id="Flow_${flowId}" sourceRef="${sourceRef}" targetRef="${targetRef}" />
            """
            .indent(4);
    return Substitutor.format(template, Map.of(
            "flowId", flowId,
            "sourceRef", sourceRef,
            "targetRef", targetRef));
  }

  private static String buildCompensationGateway(int gatewayId, int incomingFlow,
          int sourceActivityId) {
    var template = """
            <bpmn:exclusiveGateway id="Gateway_${gatewayId}" name="Success?">
              <bpmn:incoming>Flow_${incoming}</bpmn:incoming>
              <bpmn:outgoing>Flow_Success</bpmn:outgoing>
              <bpmn:outgoing>Flow_Fail</bpmn:outgoing>
            </bpmn:exclusiveGateway>
            <bpmn:sequenceFlow id="Flow_${incoming}" sourceRef="Activity_${sourceActivityId}" targetRef="Gateway_${gatewayId}" />
            <bpmn:serviceTask id="Activity_Compensate" name="Compensate">
              <bpmn:incoming>Flow_Fail</bpmn:incoming>
              <bpmn:outgoing>Flow_Comp_End</bpmn:outgoing>
            </bpmn:serviceTask>
            <bpmn:endEvent id="EndEvent_1" name="End">
              <bpmn:incoming>Flow_Success</bpmn:incoming>
              <bpmn:incoming>Flow_Comp_End</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:sequenceFlow id="Flow_Success" name="Yes" sourceRef="Gateway_${gatewayId}" targetRef="EndEvent_1" />
            <bpmn:sequenceFlow id="Flow_Comp_End" sourceRef="Activity_Compensate" targetRef="EndEvent_1" />
            """
            .indent(4);
    return Substitutor.format(template, Map.of(
            "gatewayId", gatewayId,
            "incoming", incomingFlow,
            "sourceActivityId", sourceActivityId));
  }

  private static String buildSimpleEnd(int incomingFlow, String sourceRef) {
    var template = """
            <bpmn:endEvent id="EndEvent_1" name="End">
              <bpmn:incoming>Flow_${incoming}</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:sequenceFlow id="Flow_${incoming}" sourceRef="${sourceRef}" targetRef="EndEvent_1" />
            """
            .indent(4);
    return Substitutor.format(template, Map.of(
            "incoming", incomingFlow,
            "sourceRef", sourceRef));
  }

  private static String buildCallCounts(@Nullable Map<String, Integer> callCounts) {
    if (callCounts == null || callCounts.isEmpty()) {
      return "";
    }
    return "\n  <!-- Call Counts: " + callCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining(", ")) + " -->";
  }

  public @NonNull String forProcess(@NonNull ProcessDslObject process) {
    return generateXml(process.name(), process.compensationLogic() != null);
  }

  public @NonNull String forProcess(@NonNull ProcessDslObject process,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return generateXml(process.name(), process.compensationLogic() != null, externalCalls,
            callCounts);
  }

  public @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    return generateXml(tx.name(), tx.compensationLogic() != null);
  }

  public @NonNull String forTransaction(@NonNull TransactionDslObject tx,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return generateXml(tx.name(), tx.compensationLogic() != null, externalCalls, callCounts);
  }

  public @NonNull String forHelper(@NonNull String name) {
    return generateXml(name, false);
  }

  public @NonNull String forHelper(@NonNull String name,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    return generateXml(name, false, externalCalls, callCounts);
  }

  private String generateXml(String name, boolean hasCompensation) {
    var template = """
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
                <bpmn:serviceTask id="Activity_1" name="${name}">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:serviceTask>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1" />
            ${compensation}  </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
                  <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
                    <dc:Bounds x="173" y="102" width="36" height="36" />
                  </bpmndi:BPMNShape>
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>""";
    return Substitutor.format(template, Map.of(
            "name", name,
            "compensation", buildSimpleCompensation(hasCompensation)));
  }

  private String generateXml(String name, boolean hasCompensation,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts) {
    var template = """
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
            ${body}  </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
                  <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
                    <dc:Bounds x="173" y="102" width="36" height="36" />
                  </bpmndi:BPMNShape>
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            ${callCounts}</bpmn:definitions>""";
    return Substitutor.format(template, Map.of(
            "body", buildBody(name, hasCompensation, externalCalls),
            "callCounts", buildCallCounts(callCounts)));
  }

  private static final class Ids {

    private int activityId = 1;
    private int flowId = 1;
    private int gatewayId = 1;

    int nextActivity() {
      return activityId++;
    }

    int nextFlow() {
      return flowId++;
    }

    int nextGateway() {
      return gatewayId++;
    }
  }
}
