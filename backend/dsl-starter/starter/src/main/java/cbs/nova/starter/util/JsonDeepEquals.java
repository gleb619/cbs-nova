package cbs.nova.starter.util;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;

/**
 * Deep JSON equality used by the definition test runner (T409).
 *
 * <p>
 * Policies (fixed, documented, and unit-tested):
 * <ul>
 * <li><b>Object key order is irrelevant</b> — {@code {"a":1,"b":2}} equals {@code {"b":2,"a":1}}.
 * <li><b>Array order is significant</b> — {@code [1,2]} does not equal {@code [2,1]}.
 * <li><b>A missing key is different from a key whose value is {@code null}</b> — both directions:
 * {@code {} } does not equal {@code {"a":null}}, and {@code {"a":null}} does not equal {@code {}};
 * {@code {"a":null}} equals {@code {"a":null}}.
 * <li><b>Numbers are compared by mathematical value</b> — {@code 1} equals {@code 1.0} and
 * {@code 1.00}; both are equal to {@code 1.0} regardless of whether the source JSON spelled an
 * integer or a floating-point literal. Comparison is exact: distinct values such as {@code 0.1} and
 * {@code 0.10000001} are not equal.
 * </ul>
 */
public final class JsonDeepEquals {

  private JsonDeepEquals() {
  }

  /** Returns {@code true} when the two JSON trees are deep-equal under the documented policies. */
  public static boolean deepEquals(JsonNode actual, JsonNode expected) {
    if (actual == null || expected == null) {
      return actual == expected;
    }
    if (actual.isObject() && expected.isObject()) {
      return objectsEqual(actual, expected);
    }
    if (actual.isArray() && expected.isArray()) {
      return arraysEqual(actual, expected);
    }
    if (actual.isNumber() && expected.isNumber()) {
      return numbersEqual(actual, expected);
    }
    if (actual.isTextual() && expected.isTextual()) {
      return actual.textValue().equals(expected.textValue());
    }
    if (actual.isBoolean() && expected.isBoolean()) {
      return actual.booleanValue() == expected.booleanValue();
    }
    if (actual.isNull() && expected.isNull()) {
      return true;
    }
    return false;
  }

  private static boolean objectsEqual(JsonNode actual, JsonNode expected) {
    SortedSet<String> actualKeys = fieldNames(actual);
    SortedSet<String> expectedKeys = fieldNames(expected);
    if (!actualKeys.equals(expectedKeys)) {
      return false;
    }
    for (String key : actualKeys) {
      if (!deepEquals(actual.get(key), expected.get(key))) {
        return false;
      }
    }
    return true;
  }

  private static boolean arraysEqual(JsonNode actual, JsonNode expected) {
    if (actual.size() != expected.size()) {
      return false;
    }
    Iterator<JsonNode> a = actual.iterator();
    Iterator<JsonNode> b = expected.iterator();
    while (a.hasNext()) {
      if (!deepEquals(a.next(), b.next())) {
        return false;
      }
    }
    return true;
  }

  private static boolean numbersEqual(JsonNode a, JsonNode b) {
    BigDecimal av = a.decimalValue();
    BigDecimal bv = b.decimalValue();
    return av.compareTo(bv) == 0;
  }

  private static SortedSet<String> fieldNames(JsonNode node) {
    SortedSet<String> names = new TreeSet<>();
    names.addAll(node.propertyNames());
    return names;
  }
}
