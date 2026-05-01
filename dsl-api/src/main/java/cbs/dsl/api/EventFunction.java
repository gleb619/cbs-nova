package cbs.dsl.api;

import cbs.dsl.api.EventFunction.EventArg;
import cbs.dsl.api.EventFunction.EventResult;

/**
 * A code-based event implementation executed via {@link DslComponent @DslComponent} annotation
 * processing (Layer 1).
 *
 * <p>Analogous to {@link TransactionFunction}, this interface provides a typed contract for events
 * that are implemented as Java classes rather than {@code .java} DSL files. The annotation
 * processor generates an {@link EventDefinition} wrapper and SPI registration at compile time.
 *
 * <p>Events defined in {@code .java} DSL files are processed by Layer 2 ({@code DslCompiler}) and
 * do NOT implement this interface.
 *
 * @param <I> typed input carrying event parameters (must extend {@link EventArg})
 * @param <O> typed output produced by the event (must extend {@link EventResult})
 */
@FunctionalInterface
public interface EventFunction<I extends EventArg, O extends EventResult> {

  /**
   * Executes this event with the given typed input.
   *
   * @param input the event input
   * @return the event output
   */
  O execute(I input);

  /** Marker interface for typed event input records. */
  interface EventArg extends DslPayload {}

  /** Marker interface for typed event output records. */
  interface EventResult extends DslPayload {}
}
