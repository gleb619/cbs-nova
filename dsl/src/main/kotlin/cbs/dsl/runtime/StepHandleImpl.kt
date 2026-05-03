package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.StepHandle
import java.util.concurrent.CompletableFuture

class StepHandleImpl
internal constructor(
    internal var node: StepNode,
    private val steps: MutableList<StepNode>,
    private val index: Int,
) : StepHandle {
  override fun then(tx: TransactionDefinition): CompletableFuture<StepHandle> {
    val nextNode = StepNode.Direct(tx)
    val chainNode = StepNode.Chain(node, nextNode)
    steps[index] = chainNode
    node = chainNode
    return CompletableFuture.completedFuture(StepHandleImpl(chainNode, steps, index))
  }

  override fun join() {
    // No-op — execution deferred to T26c
  }
}
