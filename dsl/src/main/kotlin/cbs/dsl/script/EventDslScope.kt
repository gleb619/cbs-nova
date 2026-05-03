package cbs.dsl.script

import cbs.dsl.api.EventDefinition
import cbs.dsl.compiler.ImportScope
import cbs.dsl.runtime.EventBuilder

abstract class EventDslScope {
    internal val registeredEvents: MutableList<EventDefinition> = mutableListOf()

    val imports: Map<String, ImportScope>
        get() = currentImports.get()

    fun event(
        code: String,
        block: EventBuilder.() -> Unit,
    ): EventDefinition {
        val def = EventBuilder(code).apply(block)
        registeredEvents += def
        return def
    }

    companion object {
        private val currentImports: ThreadLocal<Map<String, ImportScope>> =
            ThreadLocal.withInitial { emptyMap() }

        fun withImports(
            imports: Map<String, ImportScope>,
            block: () -> Unit,
        ) {
            currentImports.set(imports)
            try {
                block()
            } finally {
                currentImports.remove()
            }
        }
    }
}
