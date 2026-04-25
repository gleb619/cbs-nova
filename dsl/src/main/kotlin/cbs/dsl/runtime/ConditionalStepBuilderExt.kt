package cbs.dsl.runtime

import cbs.dsl.api.context.ConditionalStepBuilder
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/** Kotlin-idiomatic infix DSL over the Java [ConditionalStepBuilder] interface. */
infix fun ConditionalStepBuilder.`when`(predicate: () -> Boolean): ConditionalStepBuilder.WhenClause =
    this.`when`(BooleanSupplier { predicate() })

infix fun ConditionalStepBuilder.orWhen(predicate: () -> Boolean): ConditionalStepBuilder.WhenClause =
    this.orWhen(BooleanSupplier { predicate() })

infix fun ConditionalStepBuilder.WhenClause.then(block: ConditionalStepBuilder.() -> Unit): ConditionalStepBuilder =
    this.then(Consumer { it.block() })

infix fun ConditionalStepBuilder.otherwise(block: ConditionalStepBuilder.() -> Unit) {
    this.otherwise(Consumer { it.block() })
}
