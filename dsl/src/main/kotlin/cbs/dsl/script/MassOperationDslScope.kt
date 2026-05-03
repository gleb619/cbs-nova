package cbs.dsl.script

import cbs.dsl.api.MassOperationDefinition
import cbs.dsl.runtime.MassOpBuilder

abstract class MassOperationDslScope {
  internal var registeredMassOperation: MassOperationDefinition? = null

  fun massOperation(code: String, block: MassOpBuilder.() -> Unit): MassOperationDefinition {
    require(registeredMassOperation == null) {
      "Only one massOperation block is allowed per script"
    }
    val def = MassOpBuilder(code).apply(block)
    registeredMassOperation = def
    return def
  }
}
