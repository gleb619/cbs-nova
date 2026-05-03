package cbs.dsl.compiler

fun main() {
  val source = GiteaRulesSource(
    gitUrl = System.getenv("GITEA_URL") ?: "http://localhost:3001/cbs/cbs-rules.git",
    branch = System.getenv("DSL_BRANCH") ?: "main",
    localCloneDir = java.io.File("build/dsl-clone"),
  )
  val result = DslCompiler(source, DslValidator(), DslScriptHost()).compile()
  if (result is CompileResult.Failure) {
    result.errors.forEach { System.err.println("[${it.file}] ${it.message}") }
    throw RuntimeException("DSL validation failed with ${result.errors.size} error(s)")
  }
  println("DSL validation passed.")
}
