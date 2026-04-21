package cbs.dsl.runner

import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext
import cbs.dsl.api.context.TransactionContext
import cbs.dsl.impl.ImplRegistry
import cbs.dsl.impl.populateFrom
import cbs.dsl.runtime.AnyHelperOutput
import cbs.dsl.runtime.DslRegistry
import cbs.dsl.runtime.MapHelperInput
import cbs.dsl.runtime.StepNode
import cbs.dsl.runtime.TransactionsScopeImpl
import kotlinx.coroutines.runBlocking

class DslRunner(
    private val registry: DslRegistry,
    private val implRegistry: ImplRegistry = ImplRegistry().also { it.populateFrom(registry) },
) {
    fun run(
        eventCode: String,
        params: Map<String, Any>,
    ): ExecutionRecord {
        val event =
            registry.events[eventCode]
                ?: throw IllegalArgumentException("Event '$eventCode' not found in registry")

        val enrichCtx = RunnableEnrichmentContext(eventCode, params)
        event.contextBlock(enrichCtx)

        val txResults = mutableListOf<String>()
        if (event.transactionsBlock != null) {
            val scope = TransactionsScopeImpl()
            runBlocking { event.transactionsBlock!!(scope) }
            val txCtx = RunnableTransactionContext(enrichCtx)
            executeSteps(scope.steps, txCtx, txResults)
            enrichCtx.enrichment.putAll(txCtx.enrichment)
        }

        val finishCtx =
            FinishContext(
                eventCode = eventCode,
                workflowExecutionId = 0L,
                performedBy = "test",
                dslVersion = "test",
                eventParameters = params,
                displayData = emptyMap(),
            )
        finishCtx.enrichment.putAll(enrichCtx.enrichment)
        event.finishBlock(finishCtx, null)

        return ExecutionRecord(
            enrichment = finishCtx.enrichment.toMap(),
            txResults = txResults,
            error = null,
        )
    }

    private fun executeSteps(
        steps: List<StepNode>,
        ctx: RunnableTransactionContext,
        results: MutableList<String>,
    ) {
        for (node in steps) executeNode(node, ctx, results)
    }

    private fun executeNode(
        node: StepNode,
        ctx: RunnableTransactionContext,
        results: MutableList<String>,
    ) {
        when (node) {
            is StepNode.Direct -> {
                val tx = node.tx
                // If the builder declared a name, check if ImplRegistry has a named override
                val impl = tx.name?.let { implRegistry.resolveTransaction(it) } ?: tx
                impl.execute(ctx)
                results.add(tx.code)
            }

            is StepNode.Chain -> {
                executeNode(node.head, ctx, results)
                executeNode(node.tail, ctx, results)
            }

            is StepNode.Conditional -> {
                val match = node.branches.firstOrNull { it.predicate() }
                when {
                    match != null -> executeNode(match.node, ctx, results)
                    node.otherwise != null -> executeNode(node.otherwise, ctx, results)
                }
            }

            is StepNode.Barrier -> {
                Unit
            }
        }
    }

    private inner class RunnableEnrichmentContext(
        eventCode: String,
        params: Map<String, Any>,
    ) : EnrichmentContext(eventCode, 0L, "test", "test", params) {
        override fun helper(
            name: String,
            params: Map<String, Any>,
        ): Any {
            val def =
                implRegistry.resolveHelper(name)
                    ?: registry.helpers[name]
                    ?: error("Helper '$name' not found")
            val output = def.execute(MapHelperInput(params, this)) as AnyHelperOutput
            return output.value
        }
    }

    private inner class RunnableTransactionContext(
        source: RunnableEnrichmentContext,
    ) : TransactionContext(source.eventCode, 0L, "test", "test", source.eventParameters, false) {
        init {
            enrichment.putAll(source.enrichment)
        }

        override fun helper(
            name: String,
            params: Map<String, Any>,
        ): Any {
            val def =
                implRegistry.resolveHelper(name)
                    ?: registry.helpers[name]
                    ?: error("Helper '$name' not found")
            val output = def.execute(MapHelperInput(params, this)) as AnyHelperOutput
            return output.value
        }
    }
}
