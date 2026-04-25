package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.ConditionalStepBuilder
import cbs.dsl.api.context.StepHandle
import cbs.dsl.api.context.TransactionsScope
import java.util.function.Consumer

/**
 * Kotlin-idiomatic overload that accepts a lambda with receiver for conditional steps.
 * Unwraps the [java.util.concurrent.CompletableFuture] returned by the Java interface.
 */
fun TransactionsScope.step(block: ConditionalStepBuilder.() -> Unit): StepHandle =
    step(Consumer { it.block() }).get()

/**
 * Kotlin-idiomatic overload that accepts a plain [TransactionDefinition] and unwraps the future.
 */
fun TransactionsScope.step(tx: TransactionDefinition): StepHandle =
    step(tx).get()
