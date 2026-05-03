package cbs.dsl.runtime

import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.TransactionContext
import cbs.dsl.api.context.TransactionDslContext
import cbs.dsl.api.context.TransactionPhase

class TransactionBuilder(override val code: String) : TransactionDefinition {
  private var _name: String? = null
  private val _parameters = mutableListOf<ParameterDefinition>()
  override val parameters: List<ParameterDefinition>
    get() = _parameters.toList()

  private var _contextBlock: (TransactionContext) -> Unit = {}
  override val contextBlock: (TransactionContext) -> Unit
    get() = _contextBlock

  private var _preview: ((TransactionContext) -> Unit)? = null
  private var _execute: ((TransactionContext) -> Unit)? = null
  private var _rollback: ((TransactionContext) -> Unit)? = null

  override val name: String?
    get() = _name

  val hasExecute: Boolean
    get() = _execute != null

  var delegateTarget: TransactionDefinition? = null

  override fun preview(ctx: TransactionContext) {
    _contextBlock(ctx)
    val dslCtx = TransactionDslContext(ctx, delegateTarget, TransactionPhase.PREVIEW)
    _preview?.invoke(dslCtx)
  }

  override fun execute(ctx: TransactionContext) {
    _contextBlock(ctx)
    val dslCtx = TransactionDslContext(ctx, delegateTarget, TransactionPhase.EXECUTE)
    _execute?.invoke(dslCtx) ?: error("Transaction '$code' has no execute block defined")
    ctx.enrichment.putAll(dslCtx.enrichment)
  }

  override fun rollback(ctx: TransactionContext) {
    _contextBlock(ctx)
    val dslCtx = TransactionDslContext(ctx, delegateTarget, TransactionPhase.ROLLBACK)
    _rollback?.invoke(dslCtx)
  }

  fun name(value: String) {
    _name = value
  }

  fun parameters(block: ParametersScope.() -> Unit) {
    _parameters += ParametersScope().apply(block).definitions
  }

  fun context(block: (TransactionContext) -> Unit) {
    _contextBlock = block
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
