package cbs.dsl.api;

import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans a class (typically a record) for fields and maps them to {@link ParameterDefinition}.
 */
public final class ParameterScanner {

  private static final Map<Class<?>, ParameterScanResult> CACHE = new ConcurrentHashMap<>();

  private ParameterScanner() {}

  public static ParameterScanResult scan(Class<?> clazz) {
    return CACHE.computeIfAbsent(clazz, ParameterScanner::doScan);
  }

  private static ParameterScanResult doScan(Class<?> clazz) {
    if (clazz.isRecord()) {
      RecordComponent[] components = clazz.getRecordComponents();
      List<ParameterDefinition> definitions =
          Arrays.stream(components)
              .map(
                  c ->
                      ParameterDefinition.mandatory(c.getName(), resolveType(c.getType())))
              .toList();
      return new ParameterScanResult(definitions);
    }

    List<ParameterDefinition> definitions =
        Arrays.stream(clazz.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .filter(f -> !f.isSynthetic())
            .map(f -> ParameterDefinition.mandatory(f.getName(), resolveType(f.getType())))
            .toList();
    return new ParameterScanResult(definitions);
  }

  private static ParameterDefinition.ParameterType resolveType(Class<?> type) {
    if (type == String.class) {
      return ParameterDefinition.ParameterType.STRING;
    }
    if (type == int.class
        || type == Integer.class
        || type == long.class
        || type == Long.class
        || type == short.class
        || type == Short.class
        || type == byte.class
        || type == Byte.class) {
      return ParameterDefinition.ParameterType.INTEGER;
    }
    if (type == BigDecimal.class
        || type == double.class
        || type == Double.class
        || type == float.class
        || type == Float.class) {
      return ParameterDefinition.ParameterType.DECIMAL;
    }
    if (type == boolean.class || type == Boolean.class) {
      return ParameterDefinition.ParameterType.BOOLEAN;
    }
    return ParameterDefinition.ParameterType.STRING;
  }

  /**
   * Result of scanning a class for parameters.
   */
  public record ParameterScanResult(List<ParameterDefinition> definitions) {}
}
