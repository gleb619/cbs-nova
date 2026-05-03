package cbs.dsl.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static collector for {@link DslDefinition} instances built during DSL file execution.
 *
 * <p>Builders' {@code build()} methods auto-register into this collector. After executing a DSL
 * file's implicit {@code main()}, the compiler or test harness drains the collector to retrieve
 * all definitions produced by that file.
 */
public final class DslDefinitionCollector {

  private static final List<DslDefinition> definitions = new CopyOnWriteArrayList<>();

  private DslDefinitionCollector() {}

  public static void register(DslDefinition def) {
    definitions.add(def);
  }

  public static List<DslDefinition> drain() {
    List<DslDefinition> copy = new ArrayList<>(definitions);
    definitions.clear();
    return copy;
  }

  public static void clear() {
    definitions.clear();
  }
}
