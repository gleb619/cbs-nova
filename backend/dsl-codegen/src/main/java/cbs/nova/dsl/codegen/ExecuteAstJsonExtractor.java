package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslObject.DslType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses preprocessed DSL source files, finds the {@code .execute(...)} body for a named
 * process/transaction, converts its JavaParser AST to a JSON tree and serializes it to a string.
 */
public final class ExecuteAstJsonExtractor {

  private ExecuteAstJsonExtractor() {
  }

  /**
   * Extracts the execute-body JSON for the given DSL entity name.
   *
   * @param preprocessedSources
   *          valid Java source strings produced by compact-source preprocessing
   * @param name
   *          process or transaction name
   * @param type
   *          DSL type, used to decide whether to look for {@code Dsl.process(...)} or
   *          {@code Dsl.transaction(...)}
   * @return compact JSON string representing the AST, or {@code "{}"} when no execute body is found
   */
  public static @NonNull String extract(
          @NonNull List<String> preprocessedSources,
          @NonNull String name,
          @NonNull DslType type) {
    String builderName = type == DslType.PROCESS ? "process" : "transaction";
    for (String source : preprocessedSources) {
      var parseResult = new JavaParser().parse(source);
      if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
        continue;
      }
      var cu = parseResult.getResult().get();
      for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
        if (!builderName.equals(call.getNameAsString()) || call.getArguments().isEmpty()) {
          continue;
        }
        Expression firstArg = call.getArgument(0);
        if (!firstArg.isStringLiteralExpr()
                || !name.equals(firstArg.asStringLiteralExpr().getValue())) {
          continue;
        }
        Optional<Expression> executeArg = findExecuteArgument(call);
        if (executeArg.isPresent()) {
          return Json.write(toTree(executeArg.get()));
        }
      }
    }
    return "{}";
  }

  private static Optional<Expression> findExecuteArgument(@NonNull MethodCallExpr builderCall) {
    Node current = builderCall;
    while (current.getParentNode().isPresent()) {
      Node parent = current.getParentNode().get();
      if (parent instanceof MethodCallExpr parentCall
              && "execute".equals(parentCall.getNameAsString())) {
        if (parentCall.getArguments().isEmpty()) {
          return Optional.empty();
        }
        return Optional.of(parentCall.getArgument(0));
      }
      current = parent;
    }
    return Optional.empty();
  }

  private static @NonNull Object toTree(@NonNull Node node) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("type", node.getClass().getSimpleName());
    node.getRange().ifPresent(range -> map.put("range",
            range.begin.line + ":" + range.begin.column + "-"
                    + range.end.line + ":" + range.end.column));

    if (node instanceof com.github.javaparser.ast.expr.SimpleName sn) {
      map.put("value", sn.getIdentifier());
    } else if (node instanceof com.github.javaparser.ast.expr.NameExpr ne) {
      map.put("value", ne.getNameAsString());
    } else if (node instanceof com.github.javaparser.ast.expr.StringLiteralExpr sl) {
      map.put("value", sl.getValue());
    } else if (node instanceof com.github.javaparser.ast.expr.IntegerLiteralExpr il) {
      map.put("value", il.getValue());
    } else if (node instanceof com.github.javaparser.ast.expr.LongLiteralExpr ll) {
      map.put("value", ll.getValue());
    } else if (node instanceof com.github.javaparser.ast.expr.DoubleLiteralExpr dl) {
      map.put("value", dl.getValue());
    } else if (node instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr bl) {
      map.put("value", bl.getValue());
    } else if (node instanceof com.github.javaparser.ast.expr.NullLiteralExpr) {
      map.put("value", null);
    } else if (node instanceof com.github.javaparser.ast.expr.CharLiteralExpr cl) {
      map.put("value", cl.getValue());
    } else if (node instanceof com.github.javaparser.ast.expr.MethodCallExpr mc) {
      map.put("name", mc.getNameAsString());
    } else if (node instanceof com.github.javaparser.ast.expr.FieldAccessExpr fa) {
      map.put("name", fa.getNameAsString());
    }

    List<Node> childNodes = node.getChildNodes();
    if (!childNodes.isEmpty()) {
      List<Object> children = new ArrayList<>(childNodes.size());
      for (Node child : childNodes) {
        children.add(toTree(child));
      }
      map.put("children", children);
    }
    return map;
  }
}
