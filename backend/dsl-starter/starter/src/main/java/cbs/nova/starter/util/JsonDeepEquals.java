package cbs.nova.starter.util;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;

public final class JsonDeepEquals {

  private JsonDeepEquals() {
  }

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
