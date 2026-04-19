package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.StepHandle

class StepHandleImpl internal constructor(
  internal var node: StepNode,
  private val steps: MutableList<StepNode>,
  private val index: Int,
) : StepHandle {

  override suspend fun then(tx: TransactionDefinition): StepHandle {
    val nextNode = StepNode.Direct(tx)
    val chainNode = StepNode.Chain(node, nextNode)
    steps[index] = chainNode
    node = chainNode
    return StepHandleImpl(chainNode, steps, index)
  }

  override suspend fun join() {
    // No-op — execution deferred to T26c
  }
}
