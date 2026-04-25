package cbs.dsl.runner

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.ConditionalStepBuilder
import cbs.dsl.api.context.StepHandle
import cbs.dsl.api.context.TransactionsScope
import cbs.dsl.runtime.ConditionalStepBuilderImpl
import cbs.dsl.runtime.DslRegistry
import cbs.dsl.runtime.StepHandleImpl
import cbs.dsl.runtime.StepNode
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class InMemoryTransactionsScope(val registry: DslRegistry) : TransactionsScope {
  private val _steps = mutableListOf<StepNode>()
  private val _context = mutableMapOf<String, Any>()

  val steps: List<StepNode>
    get() = _steps.toList()

  override fun step(tx: TransactionDefinition): CompletableFuture<StepHandle> {
    val node = StepNode.Direct(tx)
    val index = _steps.size
    _steps.add(node)
    return CompletableFuture.completedFuture(StepHandleImpl(node, _steps, index))
  }

  override fun step(block: Consumer<ConditionalStepBuilder>): CompletableFuture<StepHandle> {
    val builder = ConditionalStepBuilderImpl()
    block.accept(builder)
    val node = builder.build()
    val index = _steps.size
    _steps.add(node)
    return CompletableFuture.completedFuture(StepHandleImpl(node, _steps, index))
  }

  override fun await(vararg handles: StepHandle) {
    _steps.add(StepNode.Barrier(handles.toList()))
  }

  override operator fun get(key: String): Any? = _context[key]

  override operator fun set(key: String, value: Any) {
    _context[key] = value
  }
}
