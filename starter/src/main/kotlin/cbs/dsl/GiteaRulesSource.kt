package cbs.dsl

import cbs.dsl.api.RulesSource
import org.eclipse.jgit.api.Git
import java.io.File

/**
 * Fetches DSL rules from a Git repository using JGit.
 * Clones the repository on first use, then pulls updates on subsequent calls.
 */
class GiteaRulesSource(
    private val gitUrl: String,
    private val branch: String,
    private val localCloneDir: File,
) : RulesSource {
    override fun fetch(): List<Pair<String, String>> {
        if (!localCloneDir.exists()) {
            cloneRepository()
        } else {
            pullRepository()
        }
        return localCloneDir
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kts") }
            .map { it.relativeTo(localCloneDir).path to it.readText() }
            .toList()
    }

    private fun cloneRepository() {
        Git
            .cloneRepository()
            .setURI(gitUrl)
            .setDirectory(localCloneDir)
            .setBranch(branch)
            .setDepth(1)
            .call()
            .close()
    }

    private fun pullRepository() {
        Git.open(localCloneDir).use { git ->
            val pullResult = git.pull().call()
            if (!pullResult.isSuccessful) {
                throw IllegalStateException("Git pull failed for $gitUrl")
            }
        }
    }
}
