package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslImplType;
import cbs.dsl.api.WorkflowFunction;
import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;

/**
 * Sample workflow implementation for the PoC.
 *
 * <p>Implements {@link WorkflowFunction} with {@link DslComponent @DslComponent}. The annotation
 * processor generates a {@code SampleWorkflowDefinition} wrapper and SPI registration at compile
 * time.
 */
@DslComponent(code = "SAMPLE_WF", type = DslImplType.WORKFLOW)
public class SampleWorkflow implements WorkflowFunction<WorkflowInput, WorkflowOutput> {

  @Override
  public WorkflowOutput execute(WorkflowInput input) {
    return new WorkflowOutput(input.getCurrentState());
  }
}
