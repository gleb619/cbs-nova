package cbs.dsl.script

import cbs.dsl.compiler.ImportScope
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class ScriptHost {
    private val host = BasicJvmScriptingHost()

    fun eval(
        scriptContent: String,
        fileName: String,
    ): ResultWithDiagnostics<EvaluationResult> = eval(scriptContent, fileName, emptyMap())

    fun eval(
        scriptContent: String,
        fileName: String,
        providedImports: Map<String, ImportScope>,
    ): ResultWithDiagnostics<EvaluationResult> {
        val compilationConfig =
            when {
                fileName.endsWith(".transaction.kts") -> TransactionScriptCompilationConfiguration
                fileName.endsWith(".helper.kts") -> HelperScriptCompilationConfiguration
                fileName.endsWith(".condition.kts") -> ConditionScriptCompilationConfiguration
                fileName.endsWith(".mass.kts") -> MassOperationScriptCompilationConfiguration
                else -> EventScriptCompilationConfiguration
            }
        val evalConfig =
            if (providedImports.isEmpty()) {
                null
            } else {
                ScriptEvaluationConfiguration {
                    providedProperties("imports" to providedImports)
                }
            }
        return host.eval(scriptContent.toScriptSource(fileName), compilationConfig, evalConfig)
    }
}
