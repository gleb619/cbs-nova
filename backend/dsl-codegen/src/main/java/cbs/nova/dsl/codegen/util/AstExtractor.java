package cbs.nova.dsl.codegen.util;

import static cbs.nova.dsl.codegen.CompilerConstants.EXECUTE_METHOD_NAME;
import static cbs.nova.dsl.codegen.CompilerConstants.PROCESS_BUILDER_NAME;
import static cbs.nova.dsl.codegen.CompilerConstants.TRANSACTION_BUILDER_NAME;

import cbs.nova.dsl.DslObject.DslType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public final class AstExtractor {

  private final Json json;

  public @NonNull String extract(
          @NonNull List<String> preprocessedSources,
          @NonNull String name,
          @NonNull DslType type) {
    String builderName = type == DslType.PROCESS ? PROCESS_BUILDER_NAME : TRANSACTION_BUILDER_NAME;
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
          return json.write(toTree(executeArg.get()));
        }
      }
    }
    return "{}";
  }

  private Optional<Expression> findExecuteArgument(@NonNull MethodCallExpr builderCall) {
    Node current = builderCall;
    while (current.getParentNode().isPresent()) {
      Node parent = current.getParentNode().get();
      if (parent instanceof MethodCallExpr parentCall
              && EXECUTE_METHOD_NAME.equals(parentCall.getNameAsString())) {
        if (parentCall.getArguments().isEmpty()) {
          return Optional.empty();
        }
        return Optional.of(parentCall.getArgument(0));
      }
      current = parent;
    }

    return Optional.empty();
  }

  private @NonNull Object toTree(@NonNull Node node) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("type", node.getClass().getSimpleName());
    node.getRange()
            .ifPresent(range -> map.put("range", "%d:%d-%d:%d".formatted(
                    range.begin.line, range.begin.column, range.end.line, range.end.column)));

    switch (node) {
      case SimpleName sn -> map.put("value", sn.getIdentifier());
      case NameExpr ne -> map.put("value", ne.getNameAsString());
      case StringLiteralExpr sl -> map.put("value", sl.getValue());
      case IntegerLiteralExpr il -> map.put("value", il.getValue());
      case LongLiteralExpr ll -> map.put("value", ll.getValue());
      case DoubleLiteralExpr dl -> map.put("value", dl.getValue());
      case BooleanLiteralExpr bl -> map.put("value", bl.getValue());
      case NullLiteralExpr _ -> map.put("value", null);
      case CharLiteralExpr cl -> map.put("value", cl.getValue());
      case MethodCallExpr mc -> map.put("name", mc.getNameAsString());
      case FieldAccessExpr fa -> map.put("name", fa.getNameAsString());
      default -> {
      }
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
