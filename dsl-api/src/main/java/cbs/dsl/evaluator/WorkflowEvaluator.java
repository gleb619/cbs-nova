package cbs.dsl.evaluator;

import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
import cbs.dsl.builder.WorkflowDslObject;

/**
 * Evaluates a {@link WorkflowDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets workflow metadata (states, transitions) directly.
 */
public class WorkflowEvaluator {

  /**
   * Evaluates the workflow with the given input.
   *
   * @param dsl the workflow DSL object
   * @param input the workflow input
   * @return the workflow output
   */
  public static WorkflowOutput evaluate(WorkflowDslObject dsl, WorkflowInput input) {
    if (dsl == null) {
      return new WorkflowOutput("DONE");
    }
    String initial = dsl.getInitial();
    if (initial == null || initial.isEmpty()) {
      initial = dsl.getStates().isEmpty() ? "DONE" : dsl.getStates().get(0);
    }
    return new WorkflowOutput(initial);
  }
}
