package cbs.dsl.compiler

class ImportScope(val alias: String, private val definitions: Map<String, Any>) {
  operator fun get(code: String): Any =
      definitions[code]
          ?: definitions.entries.firstOrNull { it.key.equals(code, ignoreCase = true) }?.value
          ?: error("Import '$alias': no definition found for code '$code'")
}
