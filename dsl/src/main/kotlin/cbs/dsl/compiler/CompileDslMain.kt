package cbs.dsl.compiler

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import java.io.File

fun main() {
  val source = GiteaRulesSource(
    gitUrl = System.getenv("GITEA_URL") ?: "http://localhost:3001/cbs/cbs-rules.git",
    branch = System.getenv("DSL_BRANCH") ?: "main",
    localCloneDir = File("build/dsl-clone"),
  )
  val result = DslCompiler(source, DslValidator()).compile()
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
  val outDir = File("build/dsl-output")
  outDir.mkdirs()
  File(outDir, "registry.json").writeText(Json.encodeToString(json))
  println("DSL compiled successfully. Workflows: ${registry.workflows.size}, Events: ${registry.events.size}, Transactions: ${registry.transactions.size}, MassOps: ${registry.massOperations.size}")
}
