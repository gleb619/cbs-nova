package cbs.dsl.script

import cbs.dsl.runtime.DslRegistry

/**
 * Result of a DSL script evaluation. Either a populated registry (success) or an error message
 * (failure).
 */
sealed class EvalResult {
  data class Success(val registry: DslRegistry) : EvalResult()

  data class Failure(val message: String) : EvalResult()
}
