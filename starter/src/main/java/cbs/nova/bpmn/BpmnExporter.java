package cbs.nova.bpmn;

import cbs.dsl.runtime.DslRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Spring bean that looks up a workflow by code and exports it as BPMN 2.0 XML. No Spring
 * annotations — wired via {@link BpmnConfig}.
 */
@RequiredArgsConstructor
public class BpmnExporter {

  private final DslRegistry registry;
  private final StaticBpmnGenerator generator;


  /**
   * Exports the workflow with the given code as BPMN 2.0 XML.
   *
   * @param workflowCode the unique workflow code
   * @return BPMN 2.0 XML string
   * @throws WorkflowNotFoundException if no workflow with the given code exists
   */
  public String export(String workflowCode) {
    var workflow = registry.getWorkflows().get(workflowCode);
    if (workflow == null) {
      throw new WorkflowNotFoundException(workflowCode);
    }
    return generator.generate(workflow);
  }
}
