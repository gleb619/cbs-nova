package cbs.dsl.compiler

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.DslImpl
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.TransactionDefinition
import java.io.File
import java.net.URL
import java.util.jar.JarFile

/** Resolves code-based imports by scanning the classpath for classes annotated with @DslImpl. */
class CodeImportResolver(
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
) {
  fun resolve(directive: ImportDirective): List<Any> =
      if (directive.wildcard) {
        scanPackage(directive.path)
      } else {
        resolveClass(directive.path)
      }

  private fun resolveClass(fqcn: String): List<Any> =
      try {
        val clazz = Class.forName(fqcn, true, classLoader)
        instantiateIfAnnotated(clazz)?.let { listOf(it) } ?: emptyList()
      } catch (e: ClassNotFoundException) {
        emptyList()
      }

  private fun scanPackage(packageName: String): List<Any> {
    val path = packageName.replace('.', '/')
    val resources = classLoader.getResources(path)
    val definitions = mutableListOf<Any>()

    while (resources.hasMoreElements()) {
      val resource = resources.nextElement()
      when (resource.protocol) {
        "file" -> scanDirectory(File(resource.toURI()), packageName, definitions)
        "jar" -> scanJar(resource, path, definitions)
      }
    }
    return definitions
  }

  private fun scanDirectory(directory: File, packageName: String, definitions: MutableList<Any>) {
    directory.listFiles()?.forEach { file ->
      if (file.isDirectory) {
        scanDirectory(file, "$packageName.${file.name}", definitions)
      } else if (file.name.endsWith(".class")) {
        val className = "$packageName.${file.name.removeSuffix(".class")}"
        try {
          val clazz = Class.forName(className, true, classLoader)
          instantiateIfAnnotated(clazz)?.let { definitions.add(it) }
        } catch (e: Throwable) {
          // Ignore loading errors for individual classes during scan
        }
      }
    }
  }

  private fun scanJar(resource: URL, packagePath: String, definitions: MutableList<Any>) {
    val jarPath = resource.path.substringBefore("!").removePrefix("file:")
    JarFile(jarPath).use { jar ->
      val entries = jar.entries()
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        if (entry.name.startsWith(packagePath) && entry.name.endsWith(".class")) {
          val className = entry.name.removeSuffix(".class").replace('/', '.')
          try {
            val clazz = Class.forName(className, true, classLoader)
            instantiateIfAnnotated(clazz)?.let { definitions.add(it) }
          } catch (e: Throwable) {
            // Ignore
          }
        }
      }
    }
  }

  private fun instantiateIfAnnotated(clazz: Class<*>): Any? {
    if (!clazz.isAnnotationPresent(DslImpl::class.java)) return null

    return try {
      val instance = clazz.getDeclaredConstructor().newInstance()
      if (
          instance is TransactionDefinition ||
              instance is HelperDefinition ||
              instance is ConditionDefinition
      ) {
        instance
      } else {
        null
      }
    } catch (e: Throwable) {
      null
    }
  }
}
