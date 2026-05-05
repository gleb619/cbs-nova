package cbs.dsl.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static collector for {@link DslObject} instances built during DSL file execution.
 *
 * <p>Builders' {@code build()} methods auto-register into this collector. After executing a DSL
 * file's implicit {@code main()}, the compiler or test harness drains the collector to retrieve all
 * objects produced by that file.
 */
public final class DslDefinitionCollector {

  private static final List<DslObject> DEFINITIONS = new CopyOnWriteArrayList<>();

  private DslDefinitionCollector() {}

  public static void register(DslObject def) {
    DEFINITIONS.add(def);
  }

  public static List<DslObject> drain() {
    List<DslObject> copy = new ArrayList<>(DEFINITIONS);
    DEFINITIONS.clear();
    return copy;
  }

  public static void clear() {
    DEFINITIONS.clear();
  }
}
