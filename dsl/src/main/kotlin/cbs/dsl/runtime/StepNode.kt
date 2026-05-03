package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.StepHandle
import java.util.function.BooleanSupplier

sealed class StepNode {
  data class Direct(val tx: TransactionDefinition) : StepNode()

  data class Chain(val head: StepNode, val tail: StepNode) : StepNode()

  data class Conditional(val branches: List<Branch>, val otherwise: StepNode?) : StepNode() {
    data class Branch(val predicate: BooleanSupplier, val node: StepNode)
  }

  data class Barrier(val handles: List<StepHandle>) : StepNode()
}
