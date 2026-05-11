package cbs.dsl.api;

import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.EventTypes.EventOutput;

import java.util.Collections;

/**
 * Defines an event — a triggered operation that orchestrates transactions, context, and finish
 * behaviour.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code event { }} block or annotated
 * with {@link DslComponent} for compile-time registration.
 */
public interface EventDefinition extends StandardDslDefinition {

  /**
   * Executes this event with the given input.
   *
   * <p>The default implementation returns an empty output. Generated Definition wrappers delegate
   * to the underlying {@link EventFunction}.
   *
   * @param input the event input
   * @return the event output
   */
  default EventOutput execute(EventInput input) {
    return EventOutput.success(Collections.emptyMap());
  }

  default EventOutput preview(EventInput input) {
    return execute(input);
  }
}
