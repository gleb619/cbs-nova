package cbs.nova.dsl;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Enhanced BPMN diagram generator that includes more detailed information
 * and can visualize external calls when provided.
 */
public final class BpmnDiagramGenerator {

  private BpmnDiagramGenerator() {
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process) {
    String name = process.name();
    boolean hasComp = process.compensationLogic() != null;
    return generateXml(name, hasComp);
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process,
                                            @Nullable List<Map<String, Object>> externalCalls,
                                            @Nullable Map<String, Integer> callCounts) {
    String name = process.name();
    boolean hasComp = process.compensationLogic() != null;
    return generateXml(name, hasComp, externalCalls, callCounts);
  }

  public static @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    String name = tx.name();
    boolean hasComp = tx.compensationLogic() != null;
    return generateXml(name, hasComp);
  }

  public static @NonNull String forTransaction(@NonNull TransactionDslObject tx,
                                               @Nullable List<Map<String, Object>> externalCalls,
                                               @Nullable Map<String, Integer> callCounts) {
    String name = tx.name();
    boolean hasComp = tx.compensationLogic() != null;
    return generateXml(name, hasComp, externalCalls, callCounts);
  }

  public static @NonNull String forHelper(@NonNull String name) {
    return generateXml(name, false);
  }

  public static @NonNull String forHelper(@NonNull String name,
                                          @Nullable List<Map<String, Object>> externalCalls,
                                          @Nullable Map<String, Integer> callCounts) {
    return generateXml(name, false, externalCalls, callCounts);
  }

  private static String generateXml(String name, boolean hasCompensation) {
    String xml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\n" +
        "<bpmn:definitions xmlns:bpmn=\\\"http://www.omg.org/spec/BPMN/20100524/MODEL\\\" \\n" +
        "                  xmlns:bpmndi=\\\"http://www.omg.org/spec/BPMN/20100524/DI\\\" \\n" +
        "                  xmlns:dc=\\\"http://www.omg.org/spec/DD/20100524/DC\\\" \\n" +
        "                  xmlns:di=\\\"http://www.omg.org/spec/DD/20100524/DI\\\" \\n" +
        "                  id=\\\"Definitions_1\\\" targetNamespace=\\\"http://bpmn.io/schema/bpmn\\\">\\n" +
        "  <bpmn:process id=\\\"Process_1\\\" isExecutable=\\\"true\\\">\\n" +
        "    <bpmn:startEvent id=\\\"StartEvent_1\\\" name=\\\"Start\\\">\\n" +
        "      <bpmn:outgoing>Flow_1</bpmn:outgoing>\\n" +
        "    </bpmn:startEvent>\\n" +
        "    <bpmn:serviceTask id=\\\"Activity_1\\\" name=\\\"\" + name + \"\\\">\\n" +
        "      <bpmn:incoming>Flow_1</bpmn:incoming>\\n" +
        "      <bpmn:outgoing>Flow_2</bpmn:outgoing>\\n" +
        "    </bpmn:serviceTask>\\n" +
        "    <bpmn:sequenceFlow id=\\\"Flow_1\\\" sourceRef=\\\"StartEvent_1\\\" targetRef=\\\"Activity_1\\\" />\\n";

    if (hasCompensation) {
      xml += "    <bpmn:exclusiveGateway id=\\\"Gateway_1\\\" name=\\\"Success?\\\">\\n" +
          "      <bpmn:incoming>Flow_2</bpmn:incoming>\\n" +
          "      <bpmn:outgoing>Flow_Success</bpmn:outgoing>\\n" +
          "      <bpmn:outgoing>Flow_Fail</bpmn:outgoing>\\n" +
          "    </bpmn:exclusiveGateway>\\n" +
          "    <bpmn:sequenceFlow id=\\\"Flow_2\\\" sourceRef=\\\"Activity_1\\\" targetRef=\\\"Gateway_1\\\" />\\n" +
          "    <bpmn:serviceTask id=\\\"Activity_Compensate\\\" name=\\\"Compensate\\\">\\n" +
          "      <bpmn:incoming>Flow_Fail</bpmn:incoming>\\n" +
          "      <bpmn:outgoing>Flow_Comp_End</bpmn:outgoing>\\n" +
          "    </bpmn:serviceTask>\\n" +
          "    <bpmn:endEvent id=\\\"EndEvent_1\\\" name=\\\"End\\\">\\n" +
          "      <bpmn:incoming>Flow_Success</bpmn:incoming>\\n" +
          "      <bpmn:incoming>Flow_Comp_End</bpmn:incoming>\\n" +
          "    </bpmn:endEvent>\\n" +
          "    <bpmn:sequenceFlow id=\\\"Flow_Success\\\" name=\\\"Yes\\\" sourceRef=\\\"Gateway_1\\\" targetRef=\\\"EndEvent_1\\\" />\\n" +
          "    <bpmn:sequenceFlow id=\\\"Flow_Comp_End\\\" sourceRef=\\\"Activity_Compensate\\\" targetRef=\\\"EndEvent_1\\\" />\\n";
    } else {
      xml += "    <bpmn:endEvent id=\\\"EndEvent_1\\\" name=\\\"End\\\">\\n" +
          "      <bpmn:incoming>Flow_2</bpmn:incoming>\\n" +
          "      <bpmn:endEvent>\\n" +
          "      <bpmn:sequenceFlow id=\\\"Flow_2\\\" sourceRef=\\\"Activity_1\\\" targetRef=\\\"EndEvent_1\\\" />\\n";
    }

    xml += "  </bpmn:process>\\n" +
        "  <bpmndi:BPMNDiagram id=\\\"BPMNDiagram_1\\\">\\n" +
        "    <bpmndi:BPMNPlane id=\\\"BPMNPlane_1\\\" bpmnElement=\\\"Process_1\\\">\\n" +
        "      <bpmndi:BPMNShape id=\\\"_BPMNShape_StartEvent_2\\\" bpmnElement=\\\"StartEvent_1\\\">\\n" +
        "        <dc:Bounds x=\\\"173\\\" y=\\\"102\\\" width=\\\"36\\\" height=\\\"36\\\" />\\n" +
        "      </bpmndi:BPMNShape>\\n" +
        "    </bpmndi:BPMNPlane>\\n" +
        "  </bpmndi:BPMNDiagram>\\n" +
        "</bpmn:definitions>";
    return xml;
  }

  // Enhanced overloads for external call visualization
  private static String generateXml(String name, boolean hasCompensation,
                                    @Nullable List<Map<String, Object>> externalCalls,
                                    @Nullable Map<String, Integer> callCounts) {
    String xml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\n" +
        "<bpmn:definitions xmlns:bpmn=\\\"http://www.omg.org/spec/BPMN/20100524/MODEL\\\" \\n" +
        "                  xmlns:bpmndi=\\\"http://www.omg.org/spec/BPMN/20100524/DI\\\" \\n" +
        "                  xmlns:dc=\\\"http://www.omg.org/spec/DD/20100524/DC\\\" \\n" +
        "                  xmlns:di=\\\"http://www.omg.org/spec/DD/20100524/DI\\\" \\n" +
        "                  id=\\\"Definitions_1\\\" targetNamespace=\\\"http://bpmn.io/schema/bpmn\\\">\\n" +
        "  <bpmn:process id=\\\"Process_1\\\" isExecutable=\\\"true\\\">\\n" +
        "    <bpmn:startEvent id=\\\"StartEvent_1\\\" name=\\\"Start\\\">\\n" +
        "      <bpmn:outgoing>Flow_1</bpmn:outgoing>\\n" +
        "    </bpmn:startEvent>\\n";

    int activityId = 1;
    int gatewayId = 1;
    int sequenceFlowId = 1;

    // Add external calls as service tasks if provided
    if (externalCalls != null && !externalCalls.isEmpty()) {
      for (int i = 0; i < externalCalls.size(); i++) {
        Map<String, Object> call = externalCalls.get(i);
        String callType = (String) call.getOrDefault("type", "external");
        String callTarget = (String) call.getOrDefault("target", "unknown");
        String callOperation = (String) call.getOrDefault("operation", "call");
        
        // Truncate long targets for readability
        String displayTarget = callTarget.length() > 20 ? 
                callTarget.substring(0, 17) + "..." : callTarget;
        
        xml += "    <bpmn:serviceTask id=\\\"Activity_" + activityId + "\\\" name=\\\"\" + callType.toUpperCase() + \" " + callOperation + " (\" + displayTarget + \")\\\">\\n" +
               "      <bpmn:incoming>Flow_" + sequenceFlowId + "</bpmn:incoming>\\n" +
               "      <bpmn:outgoing>Flow_" + (sequenceFlowId + 1) + "</bpmn:outgoing>\\n" +
               "    </bpmn:serviceTask>\\n" +
               "    <bpmn:sequenceFlow id=\\\"Flow_" + sequenceFlowId + "\\\" sourceRef=\\\"Activity_" + (activityId - 1) + "\\\" targetRef=\\\"Activity_" + activityId + "\\\" />\\n";
        
        activityId++;
        sequenceFlowId += 2;
      }
    }
    
    // Add the main activity
    xml += "    <bpmn:serviceTask id=\\\"Activity_" + activityId + "\\\" name=\\\"\" + name + \"\\\">\\n" +
           "      <bpmn:incoming>Flow_" + sequenceFlowId + "</bpmn:incoming>\\n" +
           "      <bpmn:outgoing>Flow_" + (sequenceFlowId + 1) + "</bpmn:outgoing>\\n" +
           "    </bpmn:serviceTask>\\n" +
           "    <bpmn:sequenceFlow id=\\\"Flow_" + sequenceFlowId + "\\\" sourceRef=\\\"Activity_" + (activityId - 1) + "\\\" targetRef=\\\"Activity_" + activityId + "\\\" />\\n";
    
    activityId++;
    sequenceFlowId += 2;

    if (hasCompensation) {
      xml += "    <bpmn:exclusiveGateway id=\\\"Gateway_" + gatewayId + "\\\" name=\\\"Success?\\\">\\n" +
          "      <bpmn:incoming>Flow_" + sequenceFlowId + "</bpmn:incoming>\\n" +
          "      <bpmn:outgoing>Flow_Success</bpmn:outgoing>\\n" +
          "      <bpmn:outgoing>Flow_Fail</bpmn:outgoing>\\n" +
          "    </bpmn:exclusiveGateway>\\n" +
          "    <bpmn:serviceTask id=\\\"Activity_Compensate\\\" name=\\\"Compensate\\\">\\n" +
          "      <bpmn:incoming>Flow_Fail</bpmn:incoming>\\n" +
          "      <bpmn:outgoing>Flow_Comp_End</bpmn:outgoing>\\n" +
          "    </bpmn:serviceTask>\\n" +
          "    <bpmn:endEvent id=\\\"EndEvent_1\\\" name=\\\"End\\\">\\n" +
          "      <bpmn:incoming>Flow_Success</bpmn:incoming>\\n" +
          "      <bpmn:incoming>Flow_Comp_End</bpmn:incoming>\\n" +
          "    </bpmn:endEvent>\\n" +
          "    <bpmn:sequenceFlow id=\\\"Flow_Success\\\" name=\\\"Yes\\\" sourceRef=\\\"Gateway_" + gatewayId + "\\\" targetRef=\\\"EndEvent_1\\\" />\\n" +
          "    <bpmn:sequenceFlow id=\\\"Flow_Comp_End\\\" sourceRef=\\\"Activity_Compensate\\\" targetRef=\\\"EndEvent_1\\\" />\\n";
      
      gatewayId++;
      sequenceFlowId += 4;
    } else {
      xml += "    <bpmn:endEvent id=\\\"EndEvent_1\\\" name=\\\"End\\\">\\n" +
          "      <bpmn:incoming>Flow_" + sequenceFlowId + "</bpmn:incoming>\\n" +
          "      <bpmn:endEvent>\\n" +
          "      <bpmn:sequenceFlow id=\\\"Flow_" + sequenceFlowId + "\\\" sourceRef=\\\"Activity_" + (activityId - 1) + "\\\" targetRef=\\\"EndEvent_1\\\" />\\n";
      
      sequenceFlowId += 2;
    }

    xml += "  </bpmn:process>\\n" +
        "  <bpmndi:BPMNDiagram id=\\\"BPMNDiagram_1\\\">\\n" +
        "    <bpmndi:BPMNPlane id=\\\"BPMNPlane_1\\\" bpmnElement=\\\"Process_1\\\">\\n" +
        "      <bpmndi:BPMNShape id=\\\"_BPMNShape_StartEvent_2\\\" bpmnElement=\\\"StartEvent_1\\\">\\n" +
        "        <dc:Bounds x=\\\"173\\\" y=\\\"102\\\" width=\\\"36\\\" height=\\\"36\\\" />\\n" +
        "      </bpmndi:BPMNShape>\\n" +
        "    </bpmndi:BPMNPlane>\\n" +
        "  </bpmndi:BPMNDiagram>\\n" +
        "</bpmn:definitions>";
    
    // Add call counts as documentation if provided
    if (callCounts != null && !callCounts.isEmpty()) {
      String callCountsComment = "\n  <!-- Call Counts: ";
      boolean first = true;
      for (Map.Entry<String, Integer> entry : callCounts.entrySet()) {
        if (!first) {
          callCountsComment += ", ";
        }
        callCountsComment += entry.getKey() + ": " + entry.getValue();
        first = false;
      }
      callCountsComment += " -->";
      
      // Insert the comment before the closing definitions tag
      int insertPos = xml.lastIndexOf("</bpmn:definitions>");
      xml = xml.substring(0, insertPos) + callCountsComment + xml.substring(insertPos);
    }
    
    return xml;
  }
}