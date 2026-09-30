package cbs.nova.dsl.process;

import io.avaje.jsonb.Jsonb;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic, seeded {@link SignalBuffer} used by preview / hierarchy / explain mode.
 *
 * <p>
 * Signals are pre-seeded from the request timeline ({@link SeededSignal}) and become visible at a
 * fixed await-point index — no wall-clock waiting, so a preview of a signal-branching process is
 * reproducible.
 *
 * <p>
 * Delivery model: every {@link #awaitSignal} call advances an internal await-point counter; entries
 * whose {@code deliverAt} is less than or equal to that counter are considered delivered. A seeded
 * entry with {@code deliverAt == 0} is therefore visible even before the first await, so
 * {@link #getSignalPayload} and {@link #signalReceived} work from step 0 onwards.
 *
 * <p>
 * Awaiting a name that is not on the timeline fails fast with {@link SignalNotSeededException} —
 * deterministic, never a hang.
 */
public final class PreviewSignalBuffer implements SignalBuffer {

  private static final Jsonb JSONB = Jsonb.builder().build();

  private final List<SeededSignal> timeline;
  private final Map<String, Object> payloads = new ConcurrentHashMap<>();
  private final Map<String, Boolean> delivered = new ConcurrentHashMap<>();
  private int awaitIndex = 0;

  public PreviewSignalBuffer(@NonNull List<SeededSignal> timeline) {
    this.timeline = timeline == null ? List.of() : List.copyOf(timeline);
  }

  /**
   * Empty buffer — every await of an undeclared signal fails with {@link SignalNotSeededException},
   * every {@code signalReceived} is {@code false}.
   */
  public static @NonNull PreviewSignalBuffer empty() {
    return new PreviewSignalBuffer(List.of());
  }

  @Override
  public void receive(@NonNull String name, @NonNull Object payload) {
    payloads.put(name, payload);
    delivered.put(name, Boolean.TRUE);
  }

  @Override
  public @NonNull Map<String, Object> snapshot() {
    return Map.copyOf(payloads);
  }

  @Override
  public <T> @Nullable T awaitSignal(@NonNull String name, @NonNull Class<T> type) {
    awaitIndex++;
    deliverDue();
    if (!delivered.containsKey(name)) {
      throw new SignalNotSeededException(name);
    }
    return cast(payloads.get(name), name, type);
  }

  @Override
  public <T> @Nullable T getSignalPayload(@NonNull String name, @NonNull Class<T> type) {
    deliverDue();
    if (!delivered.containsKey(name)) {
      return null;
    }
    return cast(payloads.get(name), name, type);
  }

  @Override
  public boolean signalReceived(@NonNull String name) {
    deliverDue();
    return delivered.containsKey(name);
  }

  /**
   * Await-point index reached so far. Exposed for assertions and diagnostics.
   */
  public int awaitIndex() {
    return awaitIndex;
  }

  private void deliverDue() {
    for (SeededSignal seeded : timeline) {
      if (seeded.deliverIndex() <= awaitIndex && !delivered.containsKey(seeded.name())) {
        payloads.put(seeded.name(), parse(seeded));
        delivered.put(seeded.name(), Boolean.TRUE);
      }
    }
  }

  private static @Nullable Object parse(@NonNull SeededSignal seeded) {
    String json = seeded.payloadJson();
    if (json == null || json.isBlank()) {
      return null;
    }
    return JSONB.type(Object.class).fromJson(json);
  }

  @SuppressWarnings("unchecked")
  private static <T> @Nullable T cast(@Nullable Object payload, @NonNull String name,
          @NonNull Class<T> type) {
    if (payload == null) {
      return null;
    }
    if (type.isInstance(payload)) {
      return (T) payload;
    }
    if (payload instanceof Map<?, ?> map) {
      // Seeded payloads arrive as generic JSON maps; convert to a declared record type on demand.
      return convert(name, type, map);
    }
    throw new SignalPayloadTypeMismatchException(name, payload.getClass().getName(),
            type.getName());
  }

  /**
   * Converts a decoded JSON map into the signal's declared type. Avaje jsonb is used when an
   * adapter is available for the type; otherwise the canonical record constructor is invoked
   * directly by component name so plain records work without a generated adapter.
   */
  private static <T> @Nullable T convert(@NonNull String name, @NonNull Class<T> type,
          @NonNull Map<?, ?> map) {
    try {
      return JSONB.type(type).fromJson(JSONB.type(Object.class).toJson(map));
    } catch (RuntimeException ex) {
      return convertRecord(name, type, map);
    }
  }

  private static <T> @Nullable T convertRecord(@NonNull String name, @NonNull Class<T> type,
          @NonNull Map<?, ?> map) {
    if (!type.isRecord()) {
      throw new SignalPayloadTypeMismatchException(name, "JSON object", type.getName());
    }
    RecordComponent[] components = type.getRecordComponents();
    Class<?>[] paramTypes = new Class<?>[components.length];
    Object[] args = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      paramTypes[i] = components[i].getType();
      args[i] = coerce(map.get(components[i].getName()), paramTypes[i]);
    }
    try {
      var constructor = type.getDeclaredConstructor(paramTypes);
      constructor.setAccessible(true);
      return type.cast(constructor.newInstance(args));
    } catch (ReflectiveOperationException ex) {
      throw new SignalPayloadTypeMismatchException(name, "JSON object", type.getName());
    }
  }

  /**
   * Narrow a decoded JSON value to the record component type. JSON numbers decode to
   * {@code Integer}/{@code Long}/{@code Double} depending on magnitude, while record components are
   * declared with primitive types, so an exact-value conversion is required.
   */
  private static @Nullable Object coerce(@Nullable Object value, @NonNull Class<?> target) {
    if (value == null || target.isInstance(value)) {
      return value;
    }
    try {
      if (value instanceof Number number) {
        if (target == int.class || target == Integer.class) {
          return number.intValue();
        }
        if (target == long.class || target == Long.class) {
          return number.longValue();
        }
        if (target == double.class || target == Double.class) {
          return number.doubleValue();
        }
        if (target == float.class || target == Float.class) {
          return number.floatValue();
        }
        if (target == short.class || target == Short.class) {
          return number.shortValue();
        }
        if (target == byte.class || target == Byte.class) {
          return number.byteValue();
        }
      }
      if (target == String.class) {
        return String.valueOf(value);
      }
      if ((target == boolean.class || target == Boolean.class) && value instanceof String s) {
        return Boolean.parseBoolean(s);
      }
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Cannot coerce " + value + " to " + target.getName(), ex);
    }
    return value;
  }

  /**
   * Timeline view of the buffer, ordered by delivery index — used by preview diagnostics.
   */
  public @NonNull Map<String, Object> timelineSummary() {
    Map<String, Object> summary = new LinkedHashMap<>();
    for (SeededSignal seeded : timeline) {
      summary.put(seeded.name(), Map.of("deliverAt", seeded.deliverIndex(),
              "received", delivered.containsKey(seeded.name())));
    }
    return summary;
  }
}
