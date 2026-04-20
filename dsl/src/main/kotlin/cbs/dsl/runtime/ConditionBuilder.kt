package cbs.dsl.runtime

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.context.TransactionContext

class ConditionBuilder(
    override val code: String,
) : ConditionDefinition {
    private var _predicate: ((TransactionContext) -> Boolean)? = null

    fun predicate(block: (TransactionContext) -> Boolean) {
        _predicate = block
    }

    override val predicate: (TransactionContext) -> Boolean
        get() = _predicate ?: throw IllegalStateException("Condition '$code' has no predicate block defined")
}
