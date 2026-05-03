package cbs.dsl.api

import cbs.dsl.api.context.MassOperationContext

interface ItemDefinition {
  fun execute(ctx: MassOperationContext): Unit
}
