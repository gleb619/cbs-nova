package cbs.dsl.api.context

import cbs.dsl.api.TransactionDefinition

interface ConditionalStepBuilder {
  fun `when`(predicate: () -> Boolean): WhenClause

  interface WhenClause {
    infix fun then(block: ConditionalStepBuilder.() -> Unit): ConditionalStepBuilder
  }

  infix fun orWhen(predicate: () -> Boolean): WhenClause
  infix fun otherwise(block: ConditionalStepBuilder.() -> Unit)
  fun transaction(tx: TransactionDefinition)
}
