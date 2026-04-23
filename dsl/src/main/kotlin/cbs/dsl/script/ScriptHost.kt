package cbs.dsl.script

import cbs.dsl.compiler.ImportScope
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class ScriptHost {
  private val host = BasicJvmScriptingHost()

  fun eval(scriptContent: String, fileName: String): ResultWithDiagnostics<EvaluationResult> =
      eval(scriptContent, fileName, emptyMap())

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
          fileName.endsWith(".workflow.kts") -> WorkflowScriptCompilationConfiguration
          else -> EventScriptCompilationConfiguration
        }
    var result: ResultWithDiagnostics<EvaluationResult>? = null
    EventDslScope.withImports(providedImports) {
      result = host.eval(scriptContent.toScriptSource(fileName), compilationConfig, null)
    }
    return result!!
  }
}
