package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class BpmnDiagramGenerator {

  private BpmnDiagramGenerator() {
  }

  public static @NonNull String forProcess(@NonNull ProcessDslObject process) {
    String name = process.name();
    boolean hasComp = process.compensationLogic() != null;
    return generateXml(name, hasComp);
  }

  public static @NonNull String forTransaction(@NonNull TransactionDslObject tx) {
    String name = tx.name();
    boolean hasComp = tx.compensationLogic() != null;
    return generateXml(name, hasComp);
  }

  public static @NonNull String forHelper(@NonNull String name) {
    return generateXml(name, false);
  }

  private static String generateXml(String name, boolean hasCompensation) {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" \n" +
        "                  xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" \n" +
        "                  xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" \n" +
        "                  xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" \n" +
        "                  id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n" +
        "  <bpmn:process id=\"Process_1\" isExecutable=\"true\">\n" +
        "    <bpmn:startEvent id=\"StartEvent_1\" name=\"Start\">\n" +
        "      <bpmn:outgoing>Flow_1</bpmn:outgoing>\n" +
        "    </bpmn:startEvent>\n" +
        "    <bpmn:serviceTask id=\"Activity_1\" name=\"" + name + "\">\n" +
        "      <bpmn:incoming>Flow_1</bpmn:incoming>\n" +
        "      <bpmn:outgoing>Flow_2</bpmn:outgoing>\n" +
        "    </bpmn:serviceTask>\n" +
        "    <bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"Activity_1\" />\n";

    if (hasCompensation) {
      xml += "    <bpmn:exclusiveGateway id=\"Gateway_1\" name=\"Success?\">\n" +
          "      <bpmn:incoming>Flow_2</bpmn:incoming>\n" +
          "      <bpmn:outgoing>Flow_Success</bpmn:outgoing>\n" +
          "      <bpmn:outgoing>Flow_Fail</bpmn:outgoing>\n" +
          "    </bpmn:exclusiveGateway>\n" +
          "    <bpmn:sequenceFlow id=\"Flow_2\" sourceRef=\"Activity_1\" targetRef=\"Gateway_1\" />\n" +
          "    <bpmn:serviceTask id=\"Activity_Compensate\" name=\"Compensate\">\n" +
          "      <bpmn:incoming>Flow_Fail</bpmn:incoming>\n" +
          "      <bpmn:outgoing>Flow_Comp_End</bpmn:outgoing>\n" +
          "    </bpmn:serviceTask>\n" +
          "    <bpmn:sequenceFlow id=\"Flow_Fail\" name=\"No\" sourceRef=\"Gateway_1\" targetRef=\"Activity_Compensate\" />\n" +
          "    <bpmn:endEvent id=\"EndEvent_1\" name=\"End\">\n" +
          "      <bpmn:incoming>Flow_Success</bpmn:incoming>\n" +
          "      <bpmn:incoming>Flow_Comp_End</bpmn:incoming>\n" +
          "    </bpmn:endEvent>\n" +
          "    <bpmn:sequenceFlow id=\"Flow_Success\" name=\"Yes\" sourceRef=\"Gateway_1\" targetRef=\"EndEvent_1\" />\n" +
          "    <bpmn:sequenceFlow id=\"Flow_Comp_End\" sourceRef=\"Activity_Compensate\" targetRef=\"EndEvent_1\" />\n";
    } else {
      xml += "    <bpmn:endEvent id=\"EndEvent_1\" name=\"End\">\n" +
          "      <bpmn:incoming>Flow_2</bpmn:incoming>\n" +
          "    </bpmn:endEvent>\n" +
          "    <bpmn:sequenceFlow id=\"Flow_2\" sourceRef=\"Activity_1\" targetRef=\"EndEvent_1\" />\n";
    }

    xml += "  </bpmn:process>\n" +
        "  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n" +
        "    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"Process_1\">\n" +
        "      <bpmndi:BPMNShape id=\"_BPMNShape_StartEvent_2\" bpmnElement=\"StartEvent_1\">\n" +
        "        <dc:Bounds x=\"173\" y=\"102\" width=\"36\" height=\"36\" />\n" +
        "      </bpmndi:BPMNShape>\n" +
        "    </bpmndi:BPMNPlane>\n" +
        "  </bpmndi:BPMNDiagram>\n" +
        "</bpmn:definitions>";
    return xml;
  }
}
