package cbs.app.temporal.workflow

import cbs.app.temporal.activity.TransactionActivity
import cbs.app.temporal.activity.TransactionActivityInput
import cbs.app.temporal.activity.TransactionResult
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.ConditionalStepBuilder
import cbs.dsl.api.context.StepHandle
import cbs.dsl.api.context.TransactionsScope
import io.temporal.workflow.Async
import io.temporal.workflow.Promise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Temporal implementation of TransactionsScope that orchestrates transaction execution
 * via Temporal's Async.function() and Promise primitives.
 *
 * This scope maintains a context map for sharing data between transaction steps
 * and tracks failure state for error handling.
 */
class TemporalTransactionsScope(
    private val activityStub: TransactionActivity,
    private val workflowExecutionId: Long,
    private val performedBy: String,
    private val dslVersion: String,
    private val contextJson: String,
) : TransactionsScope {
    private val contextMap: MutableMap<String, Any> = mutableMapOf()

    var failed: Boolean = false
        private set

    var errorMessage: String? = null
        private set

    /**
     * Marks this scope as failed with an optional error message.
     */
    fun markFailed(message: String?) {
        failed = true
        errorMessage = message
    }

    override suspend fun step(tx: TransactionDefinition): StepHandle {
        val input =
            TransactionActivityInput(
                tx.code,
                contextJson,
                workflowExecutionId,
                performedBy,
                dslVersion,
            )
        val promise: Promise<TransactionResult> =
            Async.function(
                activityStub::executeTransaction,
                input,
            )
        return TemporalStepHandle(promise, this)
    }

    override suspend fun step(block: ConditionalStepBuilder.() -> Unit): StepHandle =
        throw UnsupportedOperationException("Conditional steps not yet supported")

    override suspend fun await(vararg handles: StepHandle) {
        if (handles.isEmpty()) {
            return
        }

        val temporalHandles = handles.map { it as TemporalStepHandle }
        val promises = temporalHandles.map { it.promise }

        // Wait for all promises to complete
        Promise.allOf(*promises.toTypedArray()).get()

        // Check results - set failed on first failure
        for (handle in temporalHandles) {
            val result = handle.promise.get()
            if (!result.success) {
                markFailed(result.errorMessage)
                break
            }
        }
    }

    override fun get(key: String): Any? = contextMap[key]

    override fun set(
        key: String,
        value: Any,
    ) {
        contextMap[key] = value
    }

    companion object {
        /**
         * Executes the suspend transactions block within the provided scope.
         * Uses runBlocking with Dispatchers.Unconfined to execute inline on the
         * current Temporal workflow thread without dispatching to a new thread.
         *
         * Called from Java via TemporalTransactionsScope.executeBlock(block, scope)
         */
        @JvmStatic
        fun executeBlock(
            block: suspend TransactionsScope.() -> Unit,
            scope: TemporalTransactionsScope,
        ) {
            runBlocking(Dispatchers.Unconfined) {
                try {
                    block(scope)
                } catch (e: Exception) {
                    scope.markFailed(e.message)
                }
            }
        }
    }
}
