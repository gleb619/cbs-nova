package cbs.dsl.compiler

import cbs.dsl.api.RulesSource

/**
 * In-memory implementation of [RulesSource] for testing.
 * Provides DSL script content directly without filesystem or git operations.
 */
class InMemoryRulesSource(
  private val scripts: Map<String, String>,
) : RulesSource {
  override fun fetch(): List<Pair<String, String>> = scripts.toList()
}
