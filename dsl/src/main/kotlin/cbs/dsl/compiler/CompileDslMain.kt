package cbs.dsl.compiler

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

fun main() {
  val source = GiteaRulesSource(
    gitUrl = System.getenv("GITEA_URL") ?: "http://localhost:3001/cbs/cbs-rules.git",
    branch = System.getenv("DSL_BRANCH") ?: "main",
    localCloneDir = java.io.File("build/dsl-clone"),
  )
  val result = DslCompiler(source, DslValidator(), DslScriptHost()).compile()
  if (result is CompileResult.Failure) {
    result.errors.forEach { System.err.println("[${it.file}] ${it.message}") }
    throw RuntimeException("DSL compilation failed with ${result.errors.size} error(s)")
  }
  val registry = (result as CompileResult.Success).registry
  val json = buildJsonObject {
    putJsonArray("workflows") { registry.workflows.keys.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("events") { registry.events.keys.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("transactions") { registry.transactions.keys.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("massOperations") { registry.massOperations.keys.forEach { add(JsonPrimitive(it)) } }
  }
  val outDir = java.io.File("build/dsl-output")
  outDir.mkdirs()
  java.io.File(outDir, "registry.json").writeText(Json.encodeToString(json))
  println("DSL compiled successfully. Workflows: ${registry.workflows.size}, Events: ${registry.events.size}, Transactions: ${registry.transactions.size}, MassOps: ${registry.massOperations.size}")
}
