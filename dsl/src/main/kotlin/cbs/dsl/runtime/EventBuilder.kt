package cbs.dsl.runtime

import cbs.dsl.api.EventDefinition
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.DisplayScope
import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext
import cbs.dsl.api.context.TransactionsScope

class EventBuilder(
    override val code: String,
) : EventDefinition {
    private val _parameters = mutableListOf<ParameterDefinition>()
    override val parameters: List<ParameterDefinition> get() = _parameters.toList()

    private var _contextBlock: (EnrichmentContext) -> Unit = {}
    override val contextBlock: (EnrichmentContext) -> Unit get() = _contextBlock

    private var _displayBlock: (DisplayScope) -> Unit = {}
    override val displayBlock: (DisplayScope) -> Unit get() = _displayBlock

    private var _transactionsBlock: (suspend TransactionsScope.() -> Unit)? = null
    override val transactionsBlock: (suspend TransactionsScope.() -> Unit)? get() = _transactionsBlock

    private var _finishBlock: (FinishContext, Throwable?) -> Unit = { _, _ -> }
    override val finishBlock: (FinishContext, Throwable?) -> Unit get() = _finishBlock

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
}

fun event(
    code: String,
    block: EventBuilder.() -> Unit,
): EventDefinition = EventBuilder(code).apply(block)
