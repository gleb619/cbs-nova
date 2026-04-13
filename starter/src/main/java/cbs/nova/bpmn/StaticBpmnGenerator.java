package cbs.nova.bpmn;

import cbs.dsl.api.TransitionRule;
import cbs.dsl.api.WorkflowDefinition;

import java.util.HashSet;
import java.util.Set;

/**
 * Generates a static BPMN 2.0 XML representation from a {@link WorkflowDefinition}. No Spring
 * annotations — plain function wired via {@link BpmnConfig}.
 */
public class StaticBpmnGenerator {

  /**
   * Generates a valid BPMN 2.0 XML string for the given workflow definition.
   *
   * @param workflow the workflow definition to convert
   * @return BPMN 2.0 XML string
   */
  public String generate(WorkflowDefinition workflow) {
    var sb = new StringBuilder();
    sb.append("""
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          targetNamespace="http://cbs-nova/bpmn">
          <bpmn:process id="%s" isExecutable="false">
        """.formatted(workflow.getCode()));

    // Start event
    sb.append("    <bpmn:startEvent id=\"start\"/>\n");

    // Sequence flow from start to initial state
    sb.append("    <bpmn:sequenceFlow id=\"sf-start-%s\" sourceRef=\"start\" targetRef=\"%s\"/>\n"
        .formatted(workflow.getInitial(), workflow.getInitial()));

    // States: userTask for non-terminal, endEvent for terminal
    Set<String> terminals = new HashSet<>(workflow.getTerminalStates());
    for (String state : workflow.getStates()) {
      if (terminals.contains(state)) {
        sb.append("    <bpmn:endEvent id=\"%s\" name=\"%s\"/>\n".formatted(state, state));
      } else {
        sb.append("    <bpmn:userTask id=\"%s\" name=\"%s\"/>\n".formatted(state, state));
      }
    }

    // Transitions as sequence flows
    for (TransitionRule t : workflow.getTransitions()) {
      sb.append(
          "    <bpmn:sequenceFlow id=\"sf-%s-%s-%s\" sourceRef=\"%s\" targetRef=\"%s\" name=\"%s\"/>\n"
              .formatted(
                  t.getFrom(),
                  t.getTo(),
                  t.getEvent().getCode(),
                  t.getFrom(),
                  t.getTo(),
                  t.getEvent().getCode()));
    }

    sb.append("  </bpmn:process>\n</bpmn:definitions>\n");
    return sb.toString();
  }
}
