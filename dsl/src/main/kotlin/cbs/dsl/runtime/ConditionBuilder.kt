package cbs.dsl.runtime

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.ConditionTypes.ConditionInput
import cbs.dsl.api.ConditionTypes.ConditionOutput
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.TransactionContext
import java.util.function.Consumer
import java.util.function.Predicate

class ConditionBuilder(val conditionCode: String) : ConditionDefinition {
  private val _parameters = mutableListOf<ParameterDefinition>()
  private var _predicate: Predicate<TransactionContext>? = null
  private var _contextBlock: Consumer<TransactionContext> = Consumer { }

  fun parameters(block: ParametersScope.() -> Unit) {
    ParametersScope().apply(block).definitions.let { _parameters.addAll(it) }
  }

  fun context(block: (TransactionContext) -> Unit) {
    _contextBlock = Consumer { block(it) }
  }

  fun predicate(block: (TransactionContext) -> Boolean) {
    _predicate = Predicate { block(it) }
  }

  override fun getCode(): String = conditionCode

  override fun getParameters(): List<ParameterDefinition> = _parameters.toList()

  override fun getContextBlock(): Consumer<TransactionContext> = _contextBlock

  override fun getPredicate(): Predicate<TransactionContext> = Predicate { ctx ->
    _contextBlock.accept(ctx)
    _predicate?.test(ctx)
        ?: throw IllegalStateException("Condition '$conditionCode' has no predicate block defined")
  }

  override fun evaluate(input: ConditionInput): ConditionOutput {
    val ctx =
      TransactionContext.transactionBuilder()
        .eventCode(input.eventCode ?: "")
        .workflowExecutionId(input.eventNumber ?: 0L)
        .performedBy("")
        .dslVersion("")
        .eventParameters(input.nonNullParams())
        .isResumed(false)
        .build()
    val result = predicate.test(ctx)
    return ConditionOutput(result)
  }
}

fun condition(code: String, block: ConditionBuilder.() -> Unit): ConditionDefinition =
    ConditionBuilder(code).apply(block)
