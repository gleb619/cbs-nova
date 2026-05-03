package cbs.dsl.script

import cbs.dsl.api.EventDefinition
import cbs.dsl.runtime.EventBuilder

abstract class EventDslScope {
  internal val registeredEvents: MutableList<EventDefinition> = mutableListOf()

  fun event(code: String, block: EventBuilder.() -> Unit): EventDefinition {
    val def = EventBuilder(code).apply(block)
    registeredEvents += def
    return def
  }
}
