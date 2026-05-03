package cbs.dsl.compiler

import cbs.dsl.api.RulesSource
import java.io.File

//@deprecated We don't need to handle git logic here, only pure dsl work allowed
@Deprecated(message = "For removal", level = DeprecationLevel.WARNING)
class GiteaRulesSource(
  private val gitUrl: String,
  private val branch: String,
  private val localCloneDir: File,
) : RulesSource {
  override fun fetch(): List<Pair<String, String>> {
    if (!localCloneDir.exists()) {
      runGit("clone", "--branch", branch, "--depth", "1", gitUrl, localCloneDir.absolutePath)
    } else {
      runGit("pull", "--ff-only", workDir = localCloneDir)
    }
    return localCloneDir.walkTopDown()
      .filter { it.isFile && it.name.endsWith(".kts") }
      .map { it.relativeTo(localCloneDir).path to it.readText() }
      .toList()
  }

  private fun runGit(vararg args: String, workDir: File? = null): String {
    val pb = ProcessBuilder("git", *args)
    workDir?.let { pb.directory(it) }
    pb.redirectErrorStream(true)
    val proc = pb.start()
    val output = proc.inputStream.bufferedReader().readText()
    val exit = proc.waitFor()
    if (exit != 0) throw IllegalStateException("git failed: $output")
    return output
  }
}
