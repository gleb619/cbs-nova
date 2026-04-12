package cbs.dsl.runtime

import cbs.dsl.api.EventDefinition
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext

class EventBuilder(override val code: String) : EventDefinition {
  private var _contextBlock: (EnrichmentContext) -> Unit = {}
  override val contextBlock: (EnrichmentContext) -> Unit get() = _contextBlock

  private var _displayBlock: (FinishContext) -> Unit = {}
  override val displayBlock: (FinishContext) -> Unit get() = _displayBlock

  private var _transactionsBlock: List<TransactionDefinition> = emptyList()
  override val transactionsBlock: List<TransactionDefinition> get() = _transactionsBlock

  private var _finishBlock: (FinishContext) -> Unit = {}
  override val finishBlock: (FinishContext) -> Unit get() = _finishBlock

  fun context(block: (EnrichmentContext) -> Unit) {
    _contextBlock = block
  }

  fun display(block: (FinishContext) -> Unit) {
    _displayBlock = block
  }

  fun transactions(vararg tx: TransactionDefinition) {
    _transactionsBlock = tx.toList()
  }

  fun finish(block: (FinishContext) -> Unit) {
    _finishBlock = block
  }
}

fun event(code: String, block: EventBuilder.() -> Unit): EventDefinition =
  EventBuilder(code).apply(block)
