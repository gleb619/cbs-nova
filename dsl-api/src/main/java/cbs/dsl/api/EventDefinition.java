package cbs.dsl.api;

import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.EventTypes.EventOutput;
import cbs.dsl.builder.EventDslObject;
import java.util.Collections;
import java.util.List;

/**
 * Defines an event — a triggered operation that orchestrates transactions and defines display,
 * context, and finish behaviour.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code event { }} block or annotated
 * with {@link DslComponent} for compile-time registration.
 */
public interface EventDefinition extends DslDefinition<EventDslObject> {

  /**
   * Canonical code used to look up this event in the registry.
   *
   * @return the event code
   */
  String getCode();

  /**
   * List of parameter definitions declared in the {@code parameters { }} block. Used for validation
   * and documentation purposes.
   *
   * @return the parameter definitions
   */
  default List<ParameterDefinition> getParameters() {
    return Collections.emptyList();
  }

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
    return new EventOutput(Collections.emptyMap());
  }

  default EventOutput preview(EventInput input) {
    return execute(input);
  }

  /**
   * Returns the DSL object representing this definition.
   *
   * @return the DSL object, or {@code null} if not available
   */
  default EventDslObject dsl() {
    throw new NullPointerException("Dsl object not added");
  }
}
