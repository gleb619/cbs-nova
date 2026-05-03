package cbs.dsl.api

import cbs.dsl.api.context.MassOperationContext

interface SourceDefinition {
  fun load(ctx: MassOperationContext): List<Map<String, Any>>
}
