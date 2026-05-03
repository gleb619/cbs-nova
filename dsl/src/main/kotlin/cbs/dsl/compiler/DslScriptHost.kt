package cbs.dsl.compiler

import cbs.dsl.runtime.DslRegistry
import javax.script.ScriptEngineManager

//@depricated We need to use gradle to compile project to executable java classes and call them from `backend module`
@Deprecated(message = "For removal", level = DeprecationLevel.WARNING)
class DslScriptHost {
  fun eval(scriptContent: String, fileName: String): DslRegistry {
    val engine = ScriptEngineManager().getEngineByExtension("kts")
      ?: error("Kotlin scripting engine not found on classpath")
    val registry = DslRegistry()
    engine.put("registry", registry)
    val fullScript = "import cbs.dsl.runtime.*\n$scriptContent"
    try {
      engine.eval(fullScript)
    } catch (e: Exception) {
      throw IllegalStateException("Script evaluation failed for '$fileName': ${e.message}", e)
    }
    return registry
  }
}
