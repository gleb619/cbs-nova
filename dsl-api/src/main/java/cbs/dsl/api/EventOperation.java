package cbs.dsl.api;

import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.EventTypes.EventOutput;

@FunctionalInterface
public interface EventOperation {

  EventOutput execute(EventInput input);
}
