package cbs.dsl.compiler

object ImportParser {
    private val IMPORT_REGEX = Regex("""^\s*//\s*#import\s+(\S+)(?:\s+as\s+(\S+))?\s*$""")

    fun parse(content: String): List<ImportDirective> =
        content.lines().mapNotNull { line ->
            val match = IMPORT_REGEX.matchEntire(line) ?: return@mapNotNull null
            val rawPath = match.groupValues[1]
            val alias = match.groupValues[2].takeIf { it.isNotEmpty() }
            if (rawPath.startsWith("framework")) return@mapNotNull null
            val wildcard = rawPath.endsWith(".*")
            val path = if (wildcard) rawPath.dropLast(2) else rawPath
            ImportDirective(path, alias, wildcard)
        }
}
