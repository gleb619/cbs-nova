package cbs.app.temporal.workflow

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.TransactionsScope

/**
 * Factory methods for creating test transaction blocks.
 * These methods return suspend lambdas that can be called from Java tests.
 */
object TestTransactionBlocks {
    /**
     * Creates a block that executes a single transaction step.
     */
    @JvmStatic
    fun singleStep(tx: TransactionDefinition): suspend TransactionsScope.() -> Unit =
        {
            step(tx)
        }

    /**
     * Creates a block that executes a single transaction step and awaits its completion.
     */
    @JvmStatic
    fun singleStepAwait(tx: TransactionDefinition): suspend TransactionsScope.() -> Unit =
        {
            val handle = step(tx)
            await(handle)
        }

    /**
     * Creates a no-op block that does nothing.
     */
    @JvmStatic
    fun noOpBlock(): suspend TransactionsScope.() -> Unit = {}
}
