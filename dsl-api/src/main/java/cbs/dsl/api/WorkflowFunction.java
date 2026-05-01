package cbs.dsl.api;

import cbs.dsl.api.WorkflowFunction.WorkflowArg;
import cbs.dsl.api.WorkflowFunction.WorkflowResult;

/**
 * A code-based workflow implementation executed via {@link DslComponent @DslComponent} annotation
 * processing (Layer 1).
 *
 * <p>Analogous to {@link TransactionFunction}, this interface provides a typed contract for
 * workflows that are implemented as Java classes rather than {@code .java} DSL files. The
 * annotation processor generates a {@link WorkflowDefinition} wrapper and SPI registration at
 * compile time.
 *
 * <p>Workflows defined in {@code .java} DSL files are processed by Layer 2 ({@code DslCompiler})
 * and do NOT implement this interface.
 *
 * @param <I> typed input carrying workflow parameters (must extend {@link WorkflowArg})
 * @param <O> typed output produced by the workflow (must extend {@link WorkflowResult})
 */
@FunctionalInterface
public interface WorkflowFunction<I extends WorkflowArg, O extends WorkflowResult> {

  /**
   * Executes a state transition based on the current state and action.
   *
   * @param input the workflow input
   * @return the workflow output
   */
  O execute(I input);

  /** Marker interface for typed workflow input records. */
  interface WorkflowArg extends DslPayload {}

  /** Marker interface for typed workflow output records. */
  interface WorkflowResult extends DslPayload {}
}
