package cbs.dsl.codegen

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/** KSP [SymbolProcessorProvider] that creates [DslComponentProcessor]. */
class DslComponentProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return DslComponentProcessor(environment.logger, environment.codeGenerator)
  }
}
