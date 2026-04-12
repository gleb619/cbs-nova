package cbs.dsl.runtime

import cbs.dsl.api.EventDefinition
import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext

class StubWorkflowActivity(val event: EventDefinition) {
  fun execute(contextSupplier: () -> Any): Any {
    val enrichCtx = EnrichmentContext(
      eventCode = event.code,
      workflowExecutionId = 0L,
      performedBy = "stub",
      dslVersion = "stub",
      eventParameters = emptyMap(),
    )
    event.contextBlock(enrichCtx)
    val finishCtx = FinishContext(
      eventCode = event.code,
      workflowExecutionId = 0L,
      performedBy = "stub",
      dslVersion = "stub",
      eventParameters = emptyMap(),
      displayData = emptyMap(),
    )
    event.finishBlock(finishCtx)
    return Unit
  }
}
