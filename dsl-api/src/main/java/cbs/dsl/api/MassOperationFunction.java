package cbs.dsl.api;

import cbs.dsl.api.MassOperationFunction.MassOperationArg;
import cbs.dsl.api.MassOperationFunction.MassOperationResult;

/**
 * A code-based mass operation implementation executed via {@link DslComponent @DslComponent}
 * annotation processing (Layer 1).
 *
 * <p>Analogous to {@link TransactionFunction}, this interface provides a typed contract for mass
 * operations that are implemented as Java classes rather than {@code .java} DSL files. The
 * annotation processor generates a {@link MassOperationDefinition} wrapper and SPI registration at
 * compile time.
 *
 * <p>Mass operations defined in {@code .java} DSL files are processed by Layer 2
 * ({@code DslCompiler}) and do NOT implement this interface.
 *
 * @param <I> typed input carrying mass operation parameters (must extend {@link MassOperationArg})
 * @param <O> typed output produced by the mass operation (must extend {@link MassOperationResult})
 */
@FunctionalInterface
public interface MassOperationFunction<I extends MassOperationArg, O extends MassOperationResult> {

  /**
   * Executes this mass operation with the given typed input.
   *
   * @param input the mass operation input
   * @return the mass operation output
   */
  O execute(I input);

  /** Marker interface for typed mass operation input records. */
  interface MassOperationArg extends DslPayload {}

  /** Marker interface for typed mass operation output records. */
  interface MassOperationResult extends DslPayload {}
}
