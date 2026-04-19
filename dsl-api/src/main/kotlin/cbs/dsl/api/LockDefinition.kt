package cbs.dsl.api

import cbs.dsl.api.context.MassOperationContext

interface LockDefinition {
  fun isLocked(ctx: MassOperationContext): Boolean
}
