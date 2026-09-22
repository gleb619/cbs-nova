package cbs.nova.dsl.model;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;

/** Cycle-safe breadth-first graph walk keyed by node name; first visit wins, order is stable. */
public final class GraphWalk {

  private GraphWalk() {
  }

  public static <N> @NonNull List<N> breadthFirst(@NonNull N root,
          @NonNull Function<N, List<N>> children, @NonNull Function<N, String> key) {
    var visited = new LinkedHashMap<String, N>();
    var pending = new ArrayDeque<N>();
    pending.add(root);
    while (!pending.isEmpty()) {
      var node = pending.poll();
      if (visited.putIfAbsent(key.apply(node), node) == null) {
        pending.addAll(children.apply(node));
      }
    }
    return List.copyOf(visited.values());
  }
}
