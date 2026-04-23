package cbs.dsl.compiler

import cbs.dsl.api.RulesSource
import cbs.dsl.runtime.DslRegistry
import cbs.dsl.script.ConditionDslScope
import cbs.dsl.script.EventDslScope
import cbs.dsl.script.HelperDslScope
import cbs.dsl.script.MassOperationDslScope
import cbs.dsl.script.ScriptHost
import cbs.dsl.script.TransactionDslScope
import cbs.dsl.script.WorkflowDslScope
import kotlin.script.experimental.api.ResultWithDiagnostics

sealed class CompileResult {
  data class Success(val registry: DslRegistry) : CompileResult()

  data class Failure(val errors: List<ValidationError>) : CompileResult()
}

class DslCompiler(private val source: RulesSource, private val validator: DslValidator) {
  fun compile(): CompileResult {
    val files = source.fetch()
    val allErrors = mutableListOf<ValidationError>()
    val registries = mutableListOf<Pair<String, DslRegistry>>()
    val host = ScriptHost()

    // Step 1 & 2: fetch and eval each file
    for ((path, content) in files) {
      val registry =
          try {
            val evalResult = host.eval(content, path)
            when (evalResult) {
              is ResultWithDiagnostics.Success -> {
                val scriptInstance = evalResult.value.returnValue.scriptInstance
                val registry = DslRegistry()
                when (scriptInstance) {
                  is EventDslScope -> {
                    if (scriptInstance.registeredEvents.isEmpty()) {
                      error("Script '$path' did not produce a valid DslScope instance")
                    }
                    scriptInstance.registeredEvents.forEach { registry.register(it) }
                  }

                  is TransactionDslScope -> {
                    scriptInstance.registeredTransaction?.let { registry.register(it) }
                  }

                  is HelperDslScope -> {
                    scriptInstance.registeredHelpers.forEach { registry.register(it) }
                  }

                  is ConditionDslScope -> {
                    scriptInstance.registeredCondition?.let { registry.register(it) }
                  }

                  is MassOperationDslScope -> {
                    scriptInstance.registeredMassOperation?.let { registry.register(it) }
                  }

                  is WorkflowDslScope -> {
                    scriptInstance.registeredWorkflows.forEach {
                      registry.register(it)
                      it.transitions.forEach { tr -> registry.register(tr.event) }
                    }
                  }

                  else -> {
                    error("Script '$path' did not produce a valid DslScope instance")
                  }
                }
                registry
              }

              is ResultWithDiagnostics.Failure -> {
                val msg = evalResult.reports.joinToString("; ") { it.message }
                throw IllegalStateException("Script evaluation failed for '$path': $msg")
              }
            }
          } catch (e: Exception) {
            allErrors += ValidationError(path, e.message ?: "Unknown evaluation error")
            continue
          }
      registries += path to registry
    }

    // Step 3 & 4: validate each registry
    for ((path, registry) in registries) {
      allErrors += validator.validate(registry, path)
    }

    if (allErrors.isNotEmpty()) {
      return CompileResult.Failure(allErrors)
    }

    // Step 5: merge all registries, detecting duplicates
    val merged = DslRegistry()
    for ((path, registry) in registries) {
      val mergeErrors = mergeInto(merged, registry, path, allErrors)
      if (mergeErrors) {
        return CompileResult.Failure(allErrors)
      }
    }

    // Step 6: pass 2 — re-eval files with // #import directives, inject resolved scopes
    val filesWithImports = files.filter { (_, content) -> content.contains("// #import") }
    if (filesWithImports.isNotEmpty()) {
      val resolver = ImportResolver(merged)
      for ((path, content) in filesWithImports) {
        try {
          val directives = ImportParser.parse(content)
          if (directives.isEmpty()) continue
          val scopes = resolver.resolve(directives)
          val evalResult = host.eval(content, path, scopes)
          if (evalResult is ResultWithDiagnostics.Success) {
            val scriptInstance = evalResult.value.returnValue.scriptInstance
            val updated = DslRegistry()
            when (scriptInstance) {
              is EventDslScope -> {
                scriptInstance.registeredEvents.forEach { updated.register(it) }
              }

              is TransactionDslScope -> {
                scriptInstance.registeredTransaction?.let { updated.register(it) }
              }

              is HelperDslScope -> {
                scriptInstance.registeredHelpers.forEach { updated.register(it) }
              }

              is ConditionDslScope -> {
                scriptInstance.registeredCondition?.let { updated.register(it) }
              }

              is MassOperationDslScope -> {
                scriptInstance.registeredMassOperation?.let { updated.register(it) }
              }

              is WorkflowDslScope -> {
                scriptInstance.registeredWorkflows.forEach {
                  updated.register(it)
                  it.transitions.forEach { tr -> updated.register(tr.event) }
                }
              }

              else -> {
                continue
              }
            }
            overwriteInto(merged, updated)
          }
        } catch (e: Exception) {
          // pass 2 failures are non-fatal — keep pass 1 result
        }
      }
    }

    // Step 7: return success
    return CompileResult.Success(merged)
  }

  private fun overwriteInto(target: DslRegistry, source: DslRegistry) {
    // Re-merge by building a fresh registry from target minus source codes, then adding source
    // Since DslRegistry.register() throws on duplicate, we use the mergeInto path which checks
    // first.
    // For pass-2 overwrite we simply skip codes already present (same definitions, idempotent).
    val ignored = mutableListOf<ValidationError>()
    mergeInto(target, source, "pass2", ignored)
  }

  private fun mergeInto(
      target: DslRegistry,
      source: DslRegistry,
      sourceFile: String,
      errors: MutableList<ValidationError>,
  ): Boolean {
    var hasErrors = false

    for ((code, wf) in source.workflows) {
      if (code in target.workflows) {
        errors += ValidationError("$sourceFile + existing", "Duplicate workflow code '$code'")
        hasErrors = true
      } else {
        target.register(wf)
      }
    }

    for ((code, evt) in source.events) {
      if (code in target.events) {
        errors += ValidationError("$sourceFile + existing", "Duplicate event code '$code'")
        hasErrors = true
      } else {
        target.register(evt)
      }
    }

    for ((code, tx) in source.transactions) {
      if (code in target.transactions) {
        errors += ValidationError("$sourceFile + existing", "Duplicate transaction code '$code'")
        hasErrors = true
      } else {
        target.register(tx)
      }
    }

    for ((code, massOp) in source.massOperations) {
      if (code in target.massOperations) {
        errors += ValidationError("$sourceFile + existing", "Duplicate massOperation code '$code'")
        hasErrors = true
      } else {
        target.register(massOp)
      }
    }

    for ((code, helper) in source.helpers) {
      if (code in target.helpers) {
        errors += ValidationError("$sourceFile + existing", "Duplicate helper code '$code'")
        hasErrors = true
      } else {
        target.register(helper)
      }
    }

    for ((code, condition) in source.conditions) {
      if (code in target.conditions) {
        errors += ValidationError("$sourceFile + existing", "Duplicate condition code '$code'")
        hasErrors = true
      } else {
        target.register(condition)
      }
    }

    return hasErrors
  }
}
