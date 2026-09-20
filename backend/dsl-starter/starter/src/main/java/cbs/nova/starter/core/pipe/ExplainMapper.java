package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.HierarchyDiagrams;
import cbs.nova.dsl.model.HierarchyReport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ExplainMapper {

  private ExplainMapper() {
  }

  public static @NonNull ExplainReport fromHierarchy(@NonNull HierarchyReport hierarchy) {
    return fromHierarchy(hierarchy, null, null);
  }

  public static @NonNull ExplainReport fromHierarchy(
          @NonNull HierarchyReport hierarchy,
          @Nullable ExplainNodeExplainer explainer,
          @Nullable Context<?> requestCtx) {
    Set<String> visited = new HashSet<>();
    visited.add(hierarchy.name());
    return fromHierarchy(hierarchy, explainer, requestCtx, visited);
  }

  private static @NonNull ExplainReport fromHierarchy(
          @NonNull HierarchyReport hierarchy,
          @Nullable ExplainNodeExplainer explainer,
          @Nullable Context<?> requestCtx,
          @NonNull Set<String> visited) {
    String mermaid = HierarchyDiagrams.mermaidNode(
            HierarchyDiagrams.kindOf(hierarchy),
            hierarchy.name(),
            hierarchy.hasCompensation(),
            hierarchy.externalCalls(),
            hierarchy.callCounts());
    List<ExplainReport> childReports;
    if (!hierarchy.children().isEmpty()) {
      childReports = hierarchy.children().stream()
              .filter(child -> visited.add(child.name()))
              .map(child -> fromHierarchy(child, explainer, requestCtx, visited))
              .toList();
    } else {
      childReports = callNodeReports(hierarchy.astTree(), explainer, requestCtx, visited);
    }
    return new ExplainReport(
            hierarchy.name(),
            resolveDescription(hierarchy.name(), hierarchy.description(), explainer, requestCtx),
            mermaid,
            childReports);
  }

  private static @NonNull ExplainReport fromCallNode(
          @NonNull CallNode node,
          @Nullable ExplainNodeExplainer explainer,
          @Nullable Context<?> requestCtx,
          @NonNull Set<String> visited) {
    String mermaid = HierarchyDiagrams.mermaidNode(
            dslTypeOf(node.kind()), node.name(), false, node.externalCalls(), null);
    List<ExplainReport> childReports = callNodeReports(node, explainer, requestCtx, visited);
    return new ExplainReport(
            node.name(),
            resolveDescription(node.name(), synthesizedDescription(node), explainer, requestCtx),
            mermaid,
            childReports);
  }

  private static @NonNull List<ExplainReport> callNodeReports(
          @Nullable CallNode node,
          @Nullable ExplainNodeExplainer explainer,
          @Nullable Context<?> requestCtx,
          @NonNull Set<String> visited) {
    if (node == null) {
      return List.of();
    }
    return node.children().stream()
            .filter(child -> visited.add(child.name()))
            .map(child -> fromCallNode(child, explainer, requestCtx, visited))
            .toList();
  }

  private static @NonNull String resolveDescription(
          @NonNull String name,
          @NonNull String fallback,
          @Nullable ExplainNodeExplainer explainer,
          @Nullable Context<?> requestCtx) {
    if (explainer == null || requestCtx == null) {
      return fallback;
    }
    ExplainReport explained = explainer.explain(name, requestCtx);
    if (explained == null || explained.description().isBlank()) {
      return fallback;
    }
    String body = explained.mermaid();
    if (body.isBlank() || Constants.EMPTY_MARKDOWN.equals(body)) {
      return explained.description();
    }
    return explained.description() + "\n\n" + body;
  }

  private static @NonNull String synthesizedDescription(@NonNull CallNode node) {
    return switch (node.kind()) {
      case HELPER -> "Helper: " + node.name();
      default -> capitalize(node.kind().name()) + ": " + node.name();
    };
  }

  private static @NonNull DslType dslTypeOf(@NonNull CallKind kind) {
    return switch (kind) {
      case PROCESS -> DslType.PROCESS;
      case TRANSACTION -> DslType.TRANSACTION;
      case FUNCTION -> DslType.FUNCTION;
      case HELPER -> DslType.OTHER;
    };
  }

  private static @NonNull String capitalize(@NonNull String value) {
    if (value.isEmpty()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
  }
}
