package cbs.dsl.api;

import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Scans a class (typically a record) for fields and maps them to {@link ParameterDefinition}. */
public final class ParameterScanner {

  private ParameterScanner() {}

  public static ParameterScanResult scan(Class<?> clazz) {
    AtomicReference<List<ParameterDefinition>> ref = new AtomicReference<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<List<ParameterDefinition>> future = executor.submit(() -> doScan(clazz));
      try {
        ref.set(future.get());
      } catch (InterruptedException | ExecutionException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    return new ParameterScanResult(ref);
  }

  private static List<ParameterDefinition> doScan(Class<?> clazz) {
    if (clazz.isRecord()) {
      RecordComponent[] components = clazz.getRecordComponents();
      return Arrays.stream(components)
          .map(c -> ParameterDefinition.mandatory(c.getName(), resolveType(c.getType())))
          .toList();
    }

    return Arrays.stream(clazz.getDeclaredFields())
        .filter(f -> !Modifier.isStatic(f.getModifiers()))
        .filter(f -> !f.isSynthetic())
        .map(f -> ParameterDefinition.mandatory(f.getName(), resolveType(f.getType())))
        .toList();
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

  /** Result of scanning a class for parameters. */
  public record ParameterScanResult(AtomicReference<List<ParameterDefinition>> ref) {
    public List<ParameterDefinition> definitions() {
      return ref.get();
    }
  }
}
