package cbs.dsl.compiler

import java.io.File

object SampleLoader {
    fun loadAll(): Map<String, String> {
        val url = SampleLoader::class.java.classLoader.getResource("samples/") ?: return emptyMap()
        val root = File(url.toURI())
        return root
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(root).path.replace('\\', '/') to it.readText() }
    }

    fun loadGroup(prefix: String): Map<String, String> = loadAll().filterKeys { it.startsWith(prefix) }
}
