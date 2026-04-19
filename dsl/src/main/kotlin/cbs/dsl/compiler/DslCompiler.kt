package cbs.dsl.compiler

import cbs.dsl.api.RulesSource
import cbs.dsl.runtime.DslRegistry
import cbs.dsl.script.EventDslScope
import cbs.dsl.script.ScriptHost
import cbs.dsl.script.TransactionDslScope
import kotlin.script.experimental.api.ResultWithDiagnostics

sealed class CompileResult {
    data class Success(
        val registry: DslRegistry,
    ) : CompileResult()

    data class Failure(
        val errors: List<ValidationError>,
    ) : CompileResult()
}

class DslCompiler(
    private val source: RulesSource,
    private val validator: DslValidator,
) {
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
                                    scriptInstance.registeredEvents.forEach { registry.register(it) }
                                }

                                is TransactionDslScope -> {
                                    scriptInstance.registeredTransaction?.let { registry.register(it) }
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

        // Step 6: return success
        return CompileResult.Success(merged)
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
