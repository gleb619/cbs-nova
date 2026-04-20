package cbs.dsl.compiler

import cbs.dsl.runtime.DslRegistry

class ImportResolver(
    private val registry: DslRegistry,
) {
    fun resolve(directives: List<ImportDirective>): Map<String, ImportScope> {
        if (directives.isEmpty()) return emptyMap()
        val allDefs: Map<String, Any> =
            registry.workflows +
                registry.events +
                registry.transactions +
                registry.massOperations +
                registry.helpers +
                registry.conditions
        return directives.associate { directive ->
            val alias = directive.alias ?: directive.path.substringAfterLast('.')
            alias to ImportScope(alias, allDefs)
        }
    }
}
