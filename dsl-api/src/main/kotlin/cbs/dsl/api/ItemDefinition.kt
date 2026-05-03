package cbs.dsl.api

import cbs.dsl.api.context.MassOperationContext

//@depricated, for removal
@Deprecated(message = "Not used", level = DeprecationLevel.WARNING)
interface ItemDefinition {
  fun execute(ctx: MassOperationContext)
}
