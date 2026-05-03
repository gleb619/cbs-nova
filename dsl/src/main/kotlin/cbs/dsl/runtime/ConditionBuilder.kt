package cbs.dsl.runtime

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.ConditionInput
import cbs.dsl.api.ConditionOutput
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.TransactionContext

class ConditionBuilder(override val code: String) : ConditionDefinition {
  private val _parameters = mutableListOf<ParameterDefinition>()
  private var _predicate: ((TransactionContext) -> Boolean)? = null
  private var _contextBlock: (TransactionContext) -> Unit = {}

  fun parameters(block: ParametersScope.() -> Unit) {
    ParametersScope().apply(block).definitions.let { _parameters.addAll(it) }
  }

  fun context(block: (TransactionContext) -> Unit) {
    _contextBlock = block
  }

  fun predicate(block: (TransactionContext) -> Boolean) {
    _predicate = block
  }

  override val parameters: List<ParameterDefinition>
    get() = _parameters.toList()

  override val contextBlock: (TransactionContext) -> Unit
    get() = _contextBlock

  override val predicate: (TransactionContext) -> Boolean
    get() = { ctx ->
      _contextBlock(ctx)
      _predicate?.invoke(ctx)
          ?: throw IllegalStateException("Condition '$code' has no predicate block defined")
    }

  override fun evaluate(input: ConditionInput): ConditionOutput {
    val ctx =
        TransactionContext(
            eventCode = input.eventCode ?: "",
            workflowExecutionId = input.eventNumber ?: 0L,
            performedBy = "",
            dslVersion = "",
            eventParameters = input.nonNullParams(),
            isResumed = false,
        )
    val result = predicate(ctx)
    return ConditionOutput(result)
  }
}

fun condition(code: String, block: ConditionBuilder.() -> Unit): ConditionDefinition =
    ConditionBuilder(code).apply(block)
