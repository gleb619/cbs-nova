package cbs.nova.dsl.codegen;

import cbs.nova.dsl.model.DiagnosticCodes;
import cbs.nova.dsl.exception.ValidationException;
import cbs.nova.dsl.model.ValidationIssue;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.registry.HelperRegistry;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SemanticValidator {

  public void validate(
          @NonNull Collection<ProcessDescriptor> processes,
          @NonNull Collection<TransactionDescriptor> transactions,
          @NonNull Collection<FunctionDescriptor> functions,
          @NonNull HelperRegistry helperRegistry) {

    var issues = new ArrayList<ValidationIssue>();

    processes.stream()
            .filter(p -> p.name().isBlank())
            .forEach(p -> issues.add(new ValidationIssue(
                    DiagnosticCodes.BLANK_PROCESS_NAME, "Process has blank name")));
    transactions.stream()
            .filter(t -> t.name().isBlank())
            .forEach(t -> issues.add(new ValidationIssue(
                    DiagnosticCodes.BLANK_TRANSACTION_NAME, "Transaction has blank name")));
    functions.stream()
            .filter(f -> f.name().isBlank())
            .forEach(f -> issues.add(new ValidationIssue(
                    DiagnosticCodes.BLANK_FUNCTION_NAME, "Function has blank name")));

    var allNames = new ArrayList<String>();
    processes.forEach(p -> allNames.add(p.name()));
    transactions.forEach(t -> allNames.add(t.name()));
    functions.forEach(f -> allNames.add(f.name()));
    var seen = new HashSet<String>();
    allNames.stream()
            .filter(n -> !seen.add(n))
            .forEach(n -> issues.add(new ValidationIssue(
                    DiagnosticCodes.DUPLICATE_NAME, "Duplicate name: " + n)));

    var functionNames = functions.stream().map(FunctionDescriptor::name)
            .collect(Collectors.toSet());
    var allKnownHelperNames = new HashSet<>(functionNames);
    helperRegistry.allNames().forEach(allKnownHelperNames::add);

    processes.forEach(
            p -> p.helperRefs().stream()
                    .filter(ref -> !allKnownHelperNames.contains(ref))
                    .forEach(
                            ref -> issues.add(new ValidationIssue(
                                    DiagnosticCodes.UNKNOWN_HELPER,
                                    "Process '" + p.name() + "' references unknown helper: "
                                            + ref))));
    transactions.forEach(
            t -> t.helperRefs().stream()
                    .filter(ref -> !allKnownHelperNames.contains(ref))
                    .forEach(
                            ref -> issues.add(new ValidationIssue(
                                    DiagnosticCodes.UNKNOWN_HELPER,
                                    "Transaction '" + t.name()
                                            + "' references unknown helper: " + ref))));

    detectCycles(functions, functionNames, issues);

    if (!issues.isEmpty()) {
      throw ValidationException.of(issues);
    }
  }

  private void detectCycles(
          @NonNull Collection<FunctionDescriptor> functions,
          @NonNull Set<String> functionNames,
          @NonNull List<ValidationIssue> issues) {
    Map<String, List<String>> graph = new HashMap<>();
    for (var fn : functions) {
      graph.put(fn.name(), List.of());
    }

    Map<String, String> color = new HashMap<>();
    functionNames.forEach(n -> color.put(n, "WHITE"));

    for (String fn : functionNames) {
      if ("WHITE".equals(color.get(fn))) {
        var stack = new ArrayList<String>();
        if (dfs(fn, graph, color, stack)) {
          issues.add(new ValidationIssue(
                  DiagnosticCodes.CIRCULAR_DEPENDENCY,
                  "Circular dependency detected involving: " + stack));
        }
      }
    }
  }

  private boolean dfs(
          String node,
          Map<String, List<String>> graph,
          Map<String, String> color,
          List<String> stack) {
    color.put(node, "GRAY");
    stack.add(node);
    for (String neighbor : graph.getOrDefault(node, List.of())) {
      if ("GRAY".equals(color.get(neighbor))) {
        return true;
      }
      if ("WHITE".equals(color.get(neighbor)) && dfs(neighbor, graph, color, stack)) {
        return true;
      }
    }
    stack.remove(node);
    color.put(node, "BLACK");
    return false;
  }
}
