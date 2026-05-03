package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.ConditionalStepBuilder

class ConditionalStepBuilderImpl : ConditionalStepBuilder {
  private val branches: MutableList<StepNode.Conditional.Branch> = mutableListOf()
  private var otherwiseNode: StepNode? = null
  private var currentBranchBody: StepNode? = null

  override fun `when`(predicate: () -> Boolean): ConditionalStepBuilder.WhenClause {
    return WhenClauseImpl(predicate)
  }

  override infix fun orWhen(predicate: () -> Boolean): ConditionalStepBuilder.WhenClause {
    return WhenClauseImpl(predicate)
  }

  override infix fun otherwise(block: ConditionalStepBuilder.() -> Unit) {
    block()
    otherwiseNode = currentBranchBody
  }

  override fun transaction(tx: TransactionDefinition) {
    currentBranchBody = StepNode.Direct(tx)
  }

  fun build(): StepNode {
    return StepNode.Conditional(branches.toList(), otherwiseNode)
  }

  private inner class WhenClauseImpl(
    private val predicate: () -> Boolean
  ) : ConditionalStepBuilder.WhenClause {

    override infix fun then(block: ConditionalStepBuilder.() -> Unit): ConditionalStepBuilder {
      block()
      currentBranchBody?.let { body ->
        branches.add(StepNode.Conditional.Branch(predicate, body))
        currentBranchBody = null
      }
      return this@ConditionalStepBuilderImpl
    }
  }
}
