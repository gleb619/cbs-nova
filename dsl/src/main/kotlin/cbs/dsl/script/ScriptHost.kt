package cbs.dsl.script

import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class ScriptHost {
    private val host = BasicJvmScriptingHost()

    fun eval(
        scriptContent: String,
        fileName: String,
    ): ResultWithDiagnostics<EvaluationResult> {
        val config =
            when {
                fileName.endsWith(".transaction.kts") -> TransactionScriptCompilationConfiguration
                else -> EventScriptCompilationConfiguration
            }
        return host.eval(scriptContent.toScriptSource(fileName), config, null)
    }
}
