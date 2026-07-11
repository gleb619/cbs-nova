package cbs.nova.dsl;

import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

public final class SemanticValidator {

  private SemanticValidator() {
  }

  public static void validate(
          @NonNull Collection<ProcessDescriptor> processes,
          @NonNull Collection<TransactionDescriptor> transactions,
          @NonNull Collection<FunctionDescriptor> functions,
          @NonNull HelperRegistry helperRegistry) {

    var errors = new ArrayList<String>();

    // 1. Non-empty names
    processes.stream()
            .filter(p -> p.name().isBlank())
            .forEach(p -> errors.add("Process has blank name"));
    transactions.stream()
            .filter(t -> t.name().isBlank())
            .forEach(t -> errors.add("Transaction has blank name"));
    functions.stream()
            .filter(f -> f.name().isBlank())
            .forEach(f -> errors.add("Function has blank name"));

    // 2. No duplicate names across all three kinds
    var allNames = new ArrayList<String>();
    processes.forEach(p -> allNames.add(p.name()));
    transactions.forEach(t -> allNames.add(t.name()));
    functions.forEach(f -> allNames.add(f.name()));
    var seen = new HashSet<String>();
    allNames.stream()
            .filter(n -> !seen.add(n))
            .forEach(n -> errors.add("Duplicate name: " + n));

    // Known helper/function names for ref validation
    var functionNames = functions.stream().map(FunctionDescriptor::name)
            .collect(Collectors.toSet());
    var allKnownHelperNames = new HashSet<>(functionNames);
    helperRegistry.allNames().forEach(allKnownHelperNames::add);

    // 3. Unknown helper refs in processes and transactions
    processes.forEach(
            p -> p.helperRefs().stream()
                    .filter(ref -> !allKnownHelperNames.contains(ref))
                    .forEach(
                            ref -> errors.add(
                                    "Process '" + p.name() + "' references unknown helper: "
                                            + ref)));
    transactions.forEach(
            t -> t.helperRefs().stream()
                    .filter(ref -> !allKnownHelperNames.contains(ref))
                    .forEach(
                            ref -> errors.add(
                                    "Transaction '"
                                            + t.name()
                                            + "' references unknown helper: "
                                            + ref)));

    // 4. Cycle detection in function dependency graph (via helperRefs that are function names)
    detectCycles(functions, functionNames, errors);

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  private static void detectCycles(
          @NonNull Collection<FunctionDescriptor> functions,
          @NonNull Set<String> functionNames,
          @NonNull List<String> errors) {
    // Build adjacency: fn name -> fn-name refs (only refs to other functions form edges).
    // Note: FunctionDescriptor has no helperRefs() field at this stage, so the graph
    // has no edges. This structure is correct for when helperRefs gets populated later.
    Map<String, List<String>> graph = new HashMap<>();
    for (var fn : functions) {
      graph.put(fn.name(), List.of());
    }

    // WHITE=unvisited, GRAY=in-stack, BLACK=done
    Map<String, String> color = new HashMap<>();
    functionNames.forEach(n -> color.put(n, "WHITE"));

    for (String fn : functionNames) {
      if ("WHITE".equals(color.get(fn))) {
        var stack = new ArrayList<String>();
        if (dfs(fn, graph, color, stack)) {
          errors.add("Circular dependency detected involving: " + stack);
        }
      }
    }
  }

  private static boolean dfs(
          String node,
          Map<String, List<String>> graph,
          Map<String, String> color,
          List<String> stack) {
    color.put(node, "GRAY");
    stack.add(node);
    for (String neighbor : graph.getOrDefault(node, List.of())) {
      if ("GRAY".equals(color.get(neighbor)))
        return true;
      if ("WHITE".equals(color.get(neighbor)) && dfs(neighbor, graph, color, stack))
        return true;
    }
    stack.remove(node);
    color.put(node, "BLACK");
    return false;
  }
}
