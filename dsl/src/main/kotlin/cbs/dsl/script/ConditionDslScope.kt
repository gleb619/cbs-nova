package cbs.dsl.script

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.runtime.ConditionBuilder

abstract class ConditionDslScope {
  internal var registeredCondition: ConditionDefinition? = null

  fun condition(code: String, block: ConditionBuilder.() -> Unit): ConditionDefinition {
    require(registeredCondition == null) { "Only one condition block is allowed per script" }
    val def = ConditionBuilder(code).apply(block)
    registeredCondition = def
    return def
  }
}
