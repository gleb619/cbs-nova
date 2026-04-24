package cbs.dsl.compiler

import cbs.dsl.api.RulesSource
import java.util.AbstractMap

/**
 * In-memory implementation of [RulesSource] for testing. Provides DSL script content directly
 * without filesystem or git operations.
 */
class InMemoryRulesSource(private val scripts: Map<String, String>) : RulesSource {
  override fun fetch(): List<Map.Entry<String, String>> {
    return scripts.entries.map { AbstractMap.SimpleEntry(it.key, it.value) }
  }
}
