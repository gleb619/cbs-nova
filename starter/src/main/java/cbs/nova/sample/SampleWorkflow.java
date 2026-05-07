package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslComponent.DslImplType;
import cbs.dsl.api.WorkflowFunction;
import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
import cbs.dsl.api.context.WorkflowContext;

/**
 * Sample workflow implementation for the PoC.
 *
 * <p>Implements {@link WorkflowFunction} with {@link DslComponent @DslComponent}. The annotation
 * processor generates a {@code SampleWorkflowDefinition} wrapper and SPI registration at compile time.
 */
@DslComponent(code = "SAMPLE_WF", type = DslImplType.WORKFLOW)
public class SampleWorkflow implements WorkflowFunction<WorkflowInput, WorkflowOutput> {

  @Override
  public WorkflowContext<WorkflowOutput> execute(WorkflowContext<WorkflowInput> input) {
    return input.toBuilder().payload(new WorkflowOutput(input.payload().getCurrentState())).build();
  }
}
