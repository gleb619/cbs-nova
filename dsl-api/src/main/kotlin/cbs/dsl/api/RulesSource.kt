package cbs.dsl.api

/**
 * Abstraction for fetching DSL script sources.
 * Implementations may fetch from git, filesystem, or memory.
 */
interface RulesSource {
  /**
   * Fetches all DSL script files as pairs of (path, content).
   *
   * @return List of path to content mappings for .kts files
   */
  fun fetch(): List<Pair<String, String>>
}