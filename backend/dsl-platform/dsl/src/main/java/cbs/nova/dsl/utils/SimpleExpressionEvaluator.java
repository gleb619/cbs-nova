package cbs.nova.dsl.utils;

import cbs.nova.dsl.JsonValue;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.json.JsonValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODO: remove
@Deprecated(forRemoval = true)
public final class SimpleExpressionEvaluator implements ExpressionEvaluator {

  private static final Pattern PLACEHOLDER = Pattern.compile("(\\$?\\{([^{}]+)\\})");
  private static final String JSON_MARKER = ".json()";

  private final ObjectMapper mapper = DslConfig.dslConfig().jsonMapper();

  @Override
  public @NonNull Object evaluate(@NonNull String expression,
          @NonNull Map<String, Object> variables) {
    Matcher matcher = PLACEHOLDER.matcher(expression);
    if (isSinglePlaceholder(expression, matcher)) {
      String inner = matcher.group(2).trim();
      if (matcher.group(1).startsWith("${")) {
        return evalExpression(inner, variables);
      }
      return resolveVariable(inner, variables);
    }
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String raw = matcher.group(1);
      String inner = matcher.group(2).trim();
      Object value;
      if (raw.startsWith("${")) {
        value = evalExpression(inner, variables);
      } else {
        value = resolveVariable(inner, variables);
      }
      String replacement = value == null ? raw : renderValue(value);
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private boolean isSinglePlaceholder(@NonNull String expression, @NonNull Matcher matcher) {
    return matcher.matches();
  }

  private String renderValue(Object value) {
    if (value instanceof JsonValue jsonValue) {
      String text = jsonValue.asString();
      return text == null ? "" : text;
    }
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros().toPlainString();
    }
    return value == null ? "" : String.valueOf(value);
  }

  private @NonNull Object resolveVariable(@NonNull String name,
          @NonNull Map<String, Object> variables) {
    if (name.contains(JSON_MARKER)) {
      Object resolved = resolveJsonPath(name, variables);
      return resolved == null ? "" : resolved;
    }
    if (variables.containsKey(name)) {
      Object value = variables.get(name);
      return value == null ? "" : value;
    }
    return "";
  }

  private @NonNull Object resolveJsonPath(@NonNull String name,
          @NonNull Map<String, Object> variables) {
    int marker = name.indexOf(JSON_MARKER);
    String rootName = name.substring(0, marker);
    Object root = variables.get(rootName);
    if (root == null) {
      return JsonValues.missing();
    }
    JsonValue current = root instanceof JsonValue jsonValue
            ? jsonValue
            : JsonValues.of(root, mapper);
    String remaining = name.substring(marker + JSON_MARKER.length());
    return navigateJsonPath(current, remaining);
  }

  private @NonNull JsonValue navigateJsonPath(@NonNull JsonValue initial, @NonNull String path) {
    JsonValue current = initial;
    int i = 0;
    int n = path.length();
    while (i < n) {
      char c = path.charAt(i);
      if (c == '.') {
        int start = ++i;
        while (i < n && path.charAt(i) != '.' && path.charAt(i) != '[') {
          i++;
        }
        String field = path.substring(start, i);
        current = current.get(field);
      } else if (c == '[') {
        int end = path.indexOf(']', i);
        if (end == -1) {
          throw new IllegalArgumentException("Unterminated array index in JSON path: " + path);
        }
        String indexText = path.substring(i + 1, end);
        int index;
        try {
          index = Integer.parseInt(indexText);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException(
                  "Array index must be an integer in JSON path: " + path);
        }
        current = current.get(index);
        i = end + 1;
      } else {
        throw new IllegalArgumentException(
                "Unexpected character '" + c + "' in JSON path: " + path);
      }
    }
    return current;
  }

  private @NonNull Object evalExpression(@NonNull String expression,
          @NonNull Map<String, Object> variables) {
    List<Token> tokens = tokenize(expression);
    Parser parser = new Parser(tokens, variables);
    Object result = parser.parseExpression();
    if (parser.hasRemaining()) {
      throw new IllegalArgumentException("Unexpected token in expression: " + expression);
    }
    return result;
  }

  private List<Token> tokenize(String expression) {
    List<Token> tokens = new ArrayList<>();
    int i = 0;
    int n = expression.length();
    while (i < n) {
      char c = expression.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '"' || c == '\'') {
        int start = i;
        char quote = c;
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < n && expression.charAt(i) != quote) {
          sb.append(expression.charAt(i));
          i++;
        }
        if (i >= n) {
          throw new IllegalArgumentException("Unterminated string literal at " + start);
        }
        i++;
        tokens.add(new Token(TokenType.STRING, sb.toString()));
        continue;
      }
      if (c == '(' || c == ')' || c == '+' || c == '-' || c == '*' || c == '/') {
        tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
        i++;
        continue;
      }
      if (Character.isDigit(c) || c == '.') {
        int start = i;
        while (i < n && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
          i++;
        }
        tokens.add(new Token(TokenType.NUMBER, expression.substring(start, i)));
        continue;
      }
      if (Character.isJavaIdentifierStart(c)) {
        int start = i;
        while (i < n && Character.isJavaIdentifierPart(expression.charAt(i))) {
          i++;
        }
        if (i < n && expression.startsWith(JSON_MARKER, i)) {
          i += JSON_MARKER.length();
          while (i < n && (Character.isJavaIdentifierPart(expression.charAt(i))
                  || expression.charAt(i) == '.' || expression.charAt(i) == '['
                  || expression.charAt(i) == ']' || Character.isDigit(expression.charAt(i)))) {
            i++;
          }
        }
        tokens.add(new Token(TokenType.IDENTIFIER, expression.substring(start, i)));
        continue;
      }
      throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + i);
    }
    return tokens;
  }

  private enum TokenType {
    NUMBER, STRING, IDENTIFIER, OPERATOR
  }

  private record Token(TokenType type, String text) {
  }

  private final class Parser {

    private final List<Token> tokens;
    private final Map<String, Object> variables;
    private int pos;

    Parser(List<Token> tokens, Map<String, Object> variables) {
      this.tokens = tokens;
      this.variables = variables;
    }

    boolean hasRemaining() {
      return pos < tokens.size();
    }

    Object parseExpression() {
      return parseAdditive();
    }

    private Object parseAdditive() {
      Object left = parseMultiplicative();
      while (match("+", "-")) {
        String op = previous().text();
        Object right = parseMultiplicative();
        left = applyBinary(op, left, right);
      }
      return left;
    }

    private Object parseMultiplicative() {
      Object left = parseUnary();
      while (match("*", "/")) {
        String op = previous().text();
        Object right = parseUnary();
        left = applyBinary(op, left, right);
      }
      return left;
    }

    private Object parseUnary() {
      if (match("+", "-")) {
        String op = previous().text();
        Object value = parseUnary();
        if ("-".equals(op)) {
          return negate(value);
        }
        return value;
      }
      return parsePrimary();
    }

    private Object parsePrimary() {
      if (match("(")) {
        Object value = parseExpression();
        if (!match(")")) {
          throw new IllegalArgumentException("Missing closing parenthesis");
        }
        return value;
      }
      Token token = consume();
      return switch (token.type()) {
        case NUMBER -> new BigDecimal(token.text());
        case STRING -> token.text();
        case IDENTIFIER -> unwrapExpressionValue(resolveVariable(token.text(), variables));
        default -> throw new IllegalArgumentException("Unexpected token: " + token.text());
      };
    }

    private Object unwrapExpressionValue(Object value) {
      if (value instanceof JsonValue jsonValue && jsonValue.isPresent()) {
        if (jsonValue.isNull()) {
          return null;
        }
        BigDecimal decimal = jsonValue.asDecimal();
        if (decimal != null) {
          return decimal;
        }
        Boolean bool = jsonValue.asBoolean();
        if (bool != null) {
          return bool;
        }
        String string = jsonValue.asString();
        if (string != null) {
          return string;
        }
      }
      return value;
    }

    private boolean match(String... ops) {
      if (pos >= tokens.size()) {
        return false;
      }
      Token token = tokens.get(pos);
      if (token.type() != TokenType.OPERATOR) {
        return false;
      }
      for (String op : ops) {
        if (op.equals(token.text())) {
          pos++;
          return true;
        }
      }
      return false;
    }

    private Token previous() {
      return tokens.get(pos - 1);
    }

    private Token consume() {
      if (pos >= tokens.size()) {
        throw new IllegalArgumentException("Unexpected end of expression");
      }
      return tokens.get(pos++);
    }

    private Object applyBinary(String op, Object left, Object right) {
      if ("+".equals(op) && (isString(left) || isString(right))) {
        return String.valueOf(left) + right;
      }
      BigDecimal a = toBigDecimal(left);
      BigDecimal b = toBigDecimal(right);
      return switch (op) {
        case "+" -> a.add(b).stripTrailingZeros();
        case "-" -> a.subtract(b).stripTrailingZeros();
        case "*" -> a.multiply(b).stripTrailingZeros();
        case "/" -> a.divide(b, MathContext.DECIMAL64).stripTrailingZeros();
        default -> throw new IllegalArgumentException("Unsupported operator: " + op);
      };
    }

    private Object negate(Object value) {
      return toBigDecimal(value).negate().stripTrailingZeros();
    }

    private boolean isString(Object value) {
      if (value instanceof String) {
        return true;
      }
      if (value instanceof JsonValue jsonValue && jsonValue.isPresent()) {
        return jsonValue.asDecimal() == null && jsonValue.asBoolean() == null
                && !jsonValue.isNull();
      }
      return false;
    }

    private BigDecimal toBigDecimal(Object value) {
      if (value instanceof BigDecimal bd) {
        return bd;
      }
      if (value instanceof JsonValue jsonValue && jsonValue.isPresent()) {
        BigDecimal decimal = jsonValue.asDecimal();
        if (decimal != null) {
          return decimal;
        }
        String string = jsonValue.asString();
        if (string != null) {
          return new BigDecimal(string);
        }
      }
      if (value instanceof Number n) {
        return BigDecimal.valueOf(n.doubleValue());
      }
      if (value instanceof String s) {
        return new BigDecimal(s);
      }
      throw new IllegalArgumentException("Cannot convert " + value + " to number");
    }
  }
}
