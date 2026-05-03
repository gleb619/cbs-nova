package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.ConditionalStepBuilder
import cbs.dsl.api.context.StepHandle
import cbs.dsl.api.context.TransactionsScope
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class TransactionsScopeImpl : TransactionsScope {
  val steps: MutableList<StepNode> = mutableListOf()
  private val context: MutableMap<String, Any> = mutableMapOf()

  override fun step(tx: TransactionDefinition): CompletableFuture<StepHandle> {
    val node = StepNode.Direct(tx)
    val index = steps.size
    steps.add(node)
    return CompletableFuture.completedFuture(StepHandleImpl(node, steps, index))
  }

  override fun step(block: Consumer<ConditionalStepBuilder>): CompletableFuture<StepHandle> {
    val builder = ConditionalStepBuilderImpl()
    block.accept(builder)
    val node = builder.build()
    val index = steps.size
    steps.add(node)
    return CompletableFuture.completedFuture(StepHandleImpl(node, steps, index))
  }

  override fun await(vararg handles: StepHandle) {
    steps.add(StepNode.Barrier(handles.toList()))
  }

  override operator fun get(key: String): Any? = context[key]

  override operator fun set(key: String, value: Any) {
    context[key] = value
  }
}
