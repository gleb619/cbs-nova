package cbs.dsl.script

import cbs.dsl.api.HelperDefinition
import cbs.dsl.runtime.HelperBuilder

abstract class HelperDslScope {
  internal val registeredHelpers: MutableList<HelperDefinition> = mutableListOf()

  fun helpers(block: HelperDslScope.() -> Unit) = this.block()

  fun helper(code: String, block: HelperBuilder.() -> Unit): HelperDefinition {
    val def = HelperBuilder(code).apply(block)
    registeredHelpers += def
    return def
  }
}
