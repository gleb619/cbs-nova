package cbs.app.temporal.workflow

import cbs.app.temporal.activity.TransactionResult
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.StepHandle
import io.temporal.workflow.Promise

/**
 * Temporal implementation of StepHandle that wraps a Promise<TransactionResult>.
 * Provides chaining via [then] and blocking await via [join].
 */
class TemporalStepHandle(
    val promise: Promise<TransactionResult>,
    private val scope: TemporalTransactionsScope
) : StepHandle {

    override suspend fun then(tx: TransactionDefinition): StepHandle {
        // Chain: wait for this promise to complete, then create new step
        promise.get()
        return scope.step(tx)
    }

    override suspend fun join() {
        val result = promise.get()
        if (!result.success) {
            scope.markFailed(result.errorMessage)
        }
    }
}
