package cbs.dsl.compiler

import cbs.dsl.api.ImportType

object ImportParser {
  private val IMPORT_REGEX = Regex("""^\s*//\s*#import\s+(?:(code):)?(\S+)(?:\s+as\s+(\S+))?\s*$""")

  fun parse(content: String): List<ImportDirective> =
      content.lines().mapNotNull { line ->
        val match = IMPORT_REGEX.matchEntire(line) ?: return@mapNotNull null
        val typePrefix = match.groupValues[1]
        val rawPath = match.groupValues[2]
        val alias = match.groupValues[3].takeIf { it.isNotEmpty() }

        if (rawPath.startsWith("framework")) return@mapNotNull null

        val type = if (typePrefix == "code") ImportType.CODE else ImportType.DSL
        val wildcard = rawPath.endsWith(".*")
        val path = if (wildcard) rawPath.dropLast(2) else rawPath

        ImportDirective(path, alias, wildcard, type)
      }
}
