package cbs.nova.sample;

import cbs.dsl.api.Action;
import cbs.dsl.api.TransitionRuleDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;

import java.util.List;

/**
 * Sample workflow definition for PoC testing.
 *
 * <p>Defines a simple two-state workflow (INIT → DONE) triggered by {@code SAMPLE_EVENT}. In the
 * new architecture, workflows are defined via DSL files and processed by Layer 2 (DslCompiler), not
 * by {@code @DslComponent}.
 */
public class SampleWorkflow implements WorkflowDefinition {

  @Override
  public String getCode() {
    return "SAMPLE_WF";
  }

  @Override
  public List<String> getStates() {
    return List.of("INIT", "DONE");
  }

  @Override
  public String getInitial() {
    return "INIT";
  }

  @Override
  public List<String> getTerminalStates() {
    return List.of("DONE");
  }

  @Override
  public List<TransitionRuleDefinition> getTransitions() {
    return List.of(
        new TransitionRuleDefinition("INIT", "DONE", Action.CLOSE, new SampleEvent(), "FAULTED"));
  }

  @Override
  public WorkflowOutput execute(WorkflowInput input) {
    return new WorkflowOutput(input.getCurrentState());
  }
}
