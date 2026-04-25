package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.ConditionalStepBuilder
import java.util.function.BooleanSupplier
import java.util.function.Consumer

class ConditionalStepBuilderImpl : ConditionalStepBuilder {
  private val branches: MutableList<StepNode.Conditional.Branch> = mutableListOf()
  private var otherwiseNode: StepNode? = null
  private var currentBranchBody: StepNode? = null

  override fun `when`(predicate: BooleanSupplier): ConditionalStepBuilder.WhenClause =
      WhenClauseImpl(predicate)

  override fun orWhen(predicate: BooleanSupplier): ConditionalStepBuilder.WhenClause =
      WhenClauseImpl(predicate)

  override fun otherwise(block: Consumer<ConditionalStepBuilder>) {
    block.accept(this)
    otherwiseNode = currentBranchBody
  }

  override fun transaction(tx: TransactionDefinition) {
    currentBranchBody = StepNode.Direct(tx)
  }

  fun build(): StepNode = StepNode.Conditional(branches.toList(), otherwiseNode)

  private inner class WhenClauseImpl(private val predicate: BooleanSupplier) :
      ConditionalStepBuilder.WhenClause {
    override fun then(block: Consumer<ConditionalStepBuilder>): ConditionalStepBuilder {
      block.accept(this@ConditionalStepBuilderImpl)
      currentBranchBody?.let { body ->
        branches.add(StepNode.Conditional.Branch(predicate, body))
        currentBranchBody = null
      }
      return this@ConditionalStepBuilderImpl
    }
  }
}
