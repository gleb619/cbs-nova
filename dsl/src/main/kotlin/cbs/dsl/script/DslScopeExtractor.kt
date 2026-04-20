package cbs.dsl.script

import cbs.dsl.runtime.DslRegistry
import kotlin.script.experimental.api.ResultWithDiagnostics

object DslScopeExtractor {
    @JvmStatic
    fun extract(
        instance: Any?,
        fileName: String,
    ): DslRegistry {
        val registry = DslRegistry()
        when (instance) {
            is EventDslScope -> {
                instance.registeredEvents.forEach { registry.register(it) }
            }

            is TransactionDslScope -> {
                instance.registeredTransaction?.let { registry.register(it) }
            }

            is HelperDslScope -> {
                instance.registeredHelpers.forEach { registry.register(it) }
            }

            is ConditionDslScope -> {
                instance.registeredCondition?.let { registry.register(it) }
            }

            is MassOperationDslScope -> {
                instance.registeredMassOperation?.let { registry.register(it) }
            }

            is WorkflowDslScope -> {
                instance.registeredWorkflows.forEach { wf ->
                    registry.register(wf)
                    wf.transitions.forEach { tr -> registry.register(tr.event) }
                }
            }

            else -> {
                throw IllegalStateException("Script '$fileName' did not produce a valid DslScope instance")
            }
        }
        return registry
    }

    /** Java-friendly entry point: evaluates and extracts in one call, returns [EvalResult]. */
    @JvmStatic
    fun evalAndExtract(
        scriptHost: ScriptHost,
        content: String,
        fileName: String,
    ): EvalResult {
        val result = scriptHost.eval(content, fileName)
        return when (result) {
            is ResultWithDiagnostics.Success -> {
                val instance = result.value.returnValue.scriptInstance
                try {
                    EvalResult.Success(extract(instance, fileName))
                } catch (e: IllegalStateException) {
                    EvalResult.Failure(e.message ?: "Unknown error")
                }
            }

            is ResultWithDiagnostics.Failure -> {
                val msg = result.reports.joinToString("; ") { it.message }
                EvalResult.Failure(msg)
            }
        }
    }
}
