package cbs.dsl.api.context

import cbs.dsl.api.Action

open class BaseContext(
  val eventCode: String,
  val workflowExecutionId: Long,
  val performedBy: String,
  val dslVersion: String,
)
