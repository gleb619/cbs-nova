package cbs.dsl.compiler

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.DslMode
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.ImportType
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.impl.ImplRegistry
import cbs.dsl.runtime.DslRegistry

class ImportResolver(
    private val registry: DslRegistry,
    private val implRegistry: ImplRegistry? = null,
    private val mode: DslMode = DslMode.STRICT,
    private val codeImportResolver: CodeImportResolver = CodeImportResolver(),
) {
  fun resolve(directives: List<ImportDirective>): Map<String, ImportScope> {
    if (directives.isEmpty()) return emptyMap()

    val scopes = mutableMapOf<String, ImportScope>()

    for (directive in directives) {
      val alias = directive.alias ?: directive.path.substringAfterLast('.')

      val definitions =
          if (directive.type == ImportType.CODE) {
            val codeDefs =
                if (implRegistry != null) {
                  // STRICT mode: use SPI-registered ImplRegistry for lookup
                  if (directive.wildcard) {
                    implRegistry.resolveByPackagePrefix(directive.path)
                  } else {
                    implRegistry.resolveByClassName(directive.path)?.let { listOf(it) }
                        ?: emptyList()
                  }
                } else if (mode == DslMode.LENIENT) {
                  // LENIENT mode: fallback to runtime classpath scanning
                  codeImportResolver.resolve(directive)
                } else {
                  emptyList()
                }
            codeDefs.forEach { def ->
              when (def) {
                is TransactionDefinition -> registry.register(def)
                is HelperDefinition -> registry.register(def)
                is ConditionDefinition -> registry.register(def)
              }
            }
            codeDefs.associateBy { def ->
              when (def) {
                is TransactionDefinition -> def.code
                is HelperDefinition -> def.code
                is ConditionDefinition -> def.code
                else -> error("Unsupported code definition type: ${def::class}")
              }
            }
          } else {
            // DSL imports - existing logic of flattening registry
            registry.workflows +
                registry.events +
                registry.transactions +
                registry.massOperations +
                registry.helpers +
                registry.conditions
          }

      scopes[alias] = ImportScope(alias, definitions)
    }

    return scopes
  }
}
