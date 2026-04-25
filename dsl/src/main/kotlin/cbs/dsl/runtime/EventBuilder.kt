package cbs.dsl.runtime

import cbs.dsl.api.EventDefinition
import cbs.dsl.api.EventInput
import cbs.dsl.api.EventOutput
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.TransactionInput
import cbs.dsl.api.context.ConditionalStepBuilder
import cbs.dsl.api.context.DisplayScope
import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext
import cbs.dsl.api.context.StepHandle
import cbs.dsl.api.context.TransactionsScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class EventBuilder(override val code: String) : EventDefinition {
  private val _parameters = mutableListOf<ParameterDefinition>()
  override val parameters: List<ParameterDefinition>
    get() = _parameters.toList()

  private var _contextBlock: (EnrichmentContext) -> Unit = {}
  override val contextBlock: (EnrichmentContext) -> Unit
    get() = _contextBlock

  private var _displayBlock: (DisplayScope) -> Unit = {}
  override val displayBlock: (DisplayScope) -> Unit
    get() = _displayBlock

  private var _transactionsBlock: (suspend TransactionsScope.() -> Unit)? = null
  override val transactionsBlock: (suspend TransactionsScope.() -> Unit)?
    get() = _transactionsBlock

  private var _finishBlock: (FinishContext, Throwable?) -> Unit = { _, _ -> }
  override val finishBlock: (FinishContext, Throwable?) -> Unit
    get() = _finishBlock

  fun parameters(block: ParametersScope.() -> Unit) {
    _parameters += ParametersScope().apply(block).definitions
  }

  fun context(block: (EnrichmentContext) -> Unit) {
    _contextBlock = block
  }

  fun display(block: (DisplayScope) -> Unit) {
    _displayBlock = block
  }

  fun transactions(block: suspend TransactionsScope.() -> Unit) {
    _transactionsBlock = block
  }

  fun finish(block: (FinishContext, Throwable?) -> Unit) {
    _finishBlock = block
  }

  override fun execute(input: EventInput): EventOutput {
    // Build EnrichmentContext from input.params (filter nulls for Map<String, Any>)
    val params: Map<String, Any> =
        input.params.filterValues { it != null }.mapValues { it.value as Any }
    val ctx =
        EnrichmentContext.enrichmentBuilder()
          .eventCode(input.eventCode)
          .workflowExecutionId(input.eventNumber ?: 0L)
          .performedBy("")
          .dslVersion("")
          .eventParameters(params)
          .build()

    // Run context enrichment block
    contextBlock(ctx)

    // Execute transactions block and collect results
    val collectedResults = mutableMapOf<String, Map<String, Any?>>()
    transactionsBlock?.let { block ->
      val scope = ExecutingTransactionsScope(collectedResults)
      runBlocking { block(scope) }
    }

    // Build FinishContext and run finish block
    val finishCtx =
        FinishContext.finishBuilder()
          .eventCode(input.eventCode)
          .workflowExecutionId(input.eventNumber ?: 0L)
          .performedBy("")
          .dslVersion("")
          .eventParameters(params)
          .build()

    return try {
      finishBlock(finishCtx, null)
      EventOutput(ctx.enrichment.toMap(), collectedResults.toMap())
    } catch (e: Throwable) {
      EventOutput(ctx.enrichment.toMap(), collectedResults.toMap(), "FAULTED")
    }
  }

  /**
   * In-process TransactionsScope that actually executes transactions and collects results.
   * Sequential execution — join() and await() are no-ops.
   */
  private inner class ExecutingTransactionsScope(
      private val results: MutableMap<String, Map<String, Any?>>
  ) : TransactionsScope {
    private val scopeContext = mutableMapOf<String, Any>()

    override fun step(tx: TransactionDefinition): CompletableFuture<StepHandle> {
      val input = TransactionInput(emptyMap(), code, null, "0")
      val output = tx.execute(input)
      results[tx.code] = output.result
      return CompletableFuture.completedFuture(StepHandleDummy)
    }

    override fun step(block: Consumer<ConditionalStepBuilder>): CompletableFuture<StepHandle> {
      // Deferred to T58 — treat as no-op for now
      return CompletableFuture.completedFuture(StepHandleDummy)
    }

    override fun await(vararg handles: StepHandle) {
      // Sequential execution — no-op
    }

    override operator fun get(key: String): Any? = scopeContext[key]

    override operator fun set(key: String, value: Any) {
      scopeContext[key] = value
    }
  }

  /** Minimal StepHandle stub for in-process sequential execution. */
  private object StepHandleDummy : StepHandle {
    override fun then(tx: TransactionDefinition): CompletableFuture<StepHandle> =
        CompletableFuture.completedFuture(this)

    override fun join() {
      // Sequential execution — no-op
    }
  }
}

fun event(code: String, block: EventBuilder.() -> Unit): EventDefinition =
    EventBuilder(code).apply(block)
