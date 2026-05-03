package cbs.dsl.runtime

import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.TransactionInput
import cbs.dsl.api.TransactionOutput
import cbs.dsl.api.context.TransactionContext
import cbs.dsl.api.context.TransactionDslContext
import cbs.dsl.api.context.TransactionPhase
import java.util.function.Consumer

class TransactionBuilder(override val code: String) : TransactionDefinition {
  private var _name: String? = null
  private val _parameters = mutableListOf<ParameterDefinition>()
  override val parameters: List<ParameterDefinition>
    get() = _parameters.toList()

  private var _contextBlock: Consumer<TransactionContext> = Consumer { }
  override val contextBlock: Consumer<TransactionContext>
    get() = _contextBlock

  private var _preview: ((TransactionContext) -> Unit)? = null
  private var _execute: ((TransactionContext) -> Unit)? = null
  private var _rollback: ((TransactionContext) -> Unit)? = null

  override val name: String?
    get() = _name

  val hasExecute: Boolean
    get() = _execute != null

  var delegateTarget: TransactionDefinition? = null

  override fun preview(input: TransactionInput): TransactionOutput {
    val ctx = buildContext(input)
    _contextBlock.accept(ctx)
    val dslCtx = TransactionDslContext(ctx, delegateTarget, TransactionPhase.PREVIEW)
    _preview?.invoke(dslCtx)
    return TransactionOutput(dslCtx.enrichment.toMap())
  }

  override fun execute(input: TransactionInput): TransactionOutput {
    val ctx = buildContext(input)
    _contextBlock.accept(ctx)
    val dslCtx = TransactionDslContext(ctx, delegateTarget, TransactionPhase.EXECUTE)
    _execute?.invoke(dslCtx) ?: error("Transaction '$code' has no execute block defined")
    ctx.enrichment.putAll(dslCtx.enrichment)
    return TransactionOutput(dslCtx.enrichment.toMap())
  }

  override fun rollback(input: TransactionInput): TransactionOutput {
    val ctx = buildContext(input)
    _contextBlock.accept(ctx)
    val dslCtx = TransactionDslContext(ctx, delegateTarget, TransactionPhase.ROLLBACK)
    _rollback?.invoke(dslCtx)
    return TransactionOutput(dslCtx.enrichment.toMap())
  }

  private fun buildContext(input: TransactionInput): TransactionContext =
      TransactionContext(
          input.eventCode ?: "UNKNOWN",
          input.workflowExecutionId?.toLongOrNull() ?: 0L,
          "system",
          "1.0",
          input.nonNullParams(),
          false,
      )

  fun name(value: String) {
    _name = value
  }

  fun parameters(block: ParametersScope.() -> Unit) {
    _parameters += ParametersScope().apply(block).definitions
  }

  fun context(block: (TransactionContext) -> Unit) {
    _contextBlock = Consumer { block(it) }
  }

  fun preview(block: (TransactionContext) -> Unit) {
    _preview = block
  }

  fun execute(block: (TransactionContext) -> Unit) {
    _execute = block
  }

  fun rollback(block: (TransactionContext) -> Unit) {
    _rollback = block
  }
}

fun transaction(code: String, block: TransactionBuilder.() -> Unit): TransactionDefinition =
    TransactionBuilder(code).apply(block)
