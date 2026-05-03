package cbs.dsl.api;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for fetching DSL script sources. Implementations may fetch from git, filesystem, or
 * memory.
 */
public interface RulesSource {
  /**
   * Fetches all DSL script files as pairs of (path, content).
   *
   * @return List of path to content mappings for .kts files
   */
  // TODO: replace with some other internal class, not map entry
  List<Map.Entry<String, String>> fetch();
}
