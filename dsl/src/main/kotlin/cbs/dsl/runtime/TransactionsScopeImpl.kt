package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.ConditionalStepBuilder
import cbs.dsl.api.context.StepHandle
import cbs.dsl.api.context.TransactionsScope

class TransactionsScopeImpl : TransactionsScope {
  val steps: MutableList<StepNode> = mutableListOf()
  private val context: MutableMap<String, Any> = mutableMapOf()

  override suspend fun step(tx: TransactionDefinition): StepHandle {
    val node = StepNode.Direct(tx)
    val index = steps.size
    steps.add(node)
    return StepHandleImpl(node, steps, index)
  }

  override suspend fun step(block: ConditionalStepBuilder.() -> Unit): StepHandle {
    val builder = ConditionalStepBuilderImpl()
    builder.block()
    val node = builder.build()
    val index = steps.size
    steps.add(node)
    return StepHandleImpl(node, steps, index)
  }

  override suspend fun await(vararg handles: StepHandle) {
    steps.add(StepNode.Barrier(handles.toList()))
  }

  override operator fun get(key: String): Any? = context[key]

  override operator fun set(key: String, value: Any) {
    context[key] = value
  }
}
