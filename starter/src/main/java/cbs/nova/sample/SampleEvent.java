package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslImplType;
import cbs.dsl.api.EventFunction;
import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.EventTypes.EventOutput;

import java.util.Map;

/**
 * Sample event implementation for the PoC.
 *
 * <p>Implements {@link EventFunction} with {@link DslComponent @DslComponent}. The annotation
 * processor generates a {@code SampleEventDefinition} wrapper and SPI registration at compile time.
 */
@DslComponent(code = "SAMPLE_EVENT", type = DslImplType.EVENT)
public class SampleEvent implements EventFunction<EventInput, EventOutput> {

  @Override
  public EventOutput execute(EventInput input) {
    String name = input.params().getOrDefault("name", "World").toString();
    return new EventOutput(Map.of("greeting", "Hello, " + name + "!"));
  }
}
