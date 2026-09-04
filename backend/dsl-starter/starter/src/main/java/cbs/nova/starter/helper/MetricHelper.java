package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.helper.model.MetricIn;
import cbs.nova.starter.helper.model.MetricOut;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Emits a Micrometer meter from a DSL process.
 *
 * <p>
 * Supports four meter kinds via the {@code type} discriminator:
 * <ul>
 * <li>{@code "counter"} — increments a {@link Counter}.</li>
 * <li>{@code "gauge"} — updates a {@link Gauge} backed by an {@link AtomicReference}; last call
 * with the same {@code name}+{@code tags} wins.</li>
 * <li>{@code "timer"} — records a {@link Timer} duration in milliseconds.</li>
 * <li>{@code "summary"} — records a value into a {@link DistributionSummary}.</li>
 * </ul>
 *
 * <p>
 * The {@link MeterRegistry} bean is optional (Spring Boot actuator is {@code compileOnly} on the
 * starter). When absent the helper validates the input and returns {@link MetricOut#emitted()}
 * {@code false} as a no-op. Validation runs before the no-op check so bad DSL fails fast regardless
 * of whether a registry is present.
 *
 * <p>
 * Caveat on the gauge path: Micrometer holds a weak reference to the value-supplier; keeping the
 * {@link AtomicReference} field on this singleton-scoped helper makes it survive for the lifetime
 * of the Spring context. Don't pick high-cardinality tag combinations (gauge per order id etc.) —
 * the helper will pin one atomic per (name, tags) tuple for the life of the JVM.
 */
@SpringHelper(name = "metric")
public class MetricHelper implements Executable<MetricIn, MetricOut> {

  private static final Set<String> TYPES = Set.of("counter", "gauge", "timer", "summary");

  private final @Nullable MeterRegistry registry;
  private final Map<String, AtomicReference<Double>> gaugeHolders = new ConcurrentHashMap<>();

  public MetricHelper(ObjectProvider<MeterRegistry> registryProvider) {
    this.registry = registryProvider.getIfAvailable();
  }

  MetricHelper(@Nullable MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public @NonNull Result<MetricOut> execute(@NonNull Context<MetricIn> ctx) {
    try {
      MetricIn input = ctx.body();
      validate(input);

      if (registry == null) {
        return Result.success(new MetricOut(false));
      }

      String type = input.type().toLowerCase(Locale.ROOT);
      Iterable<Tag> tags = toTags(input.effectiveTags());
      switch (type) {
        case "counter" -> {
          long amount = input.amount() == null ? 1L : input.amount();
          Counter.builder(input.name()).tags(tags).register(registry).increment(amount);
        }
        case "gauge" -> {
          AtomicReference<Double> holder = gaugeHolder(input.name(), tags);
          holder.set(input.value());
        }
        case "timer" -> {
          Timer.builder(input.name())
                  .tags(tags)
                  .register(registry)
                  .record(Duration.ofMillis(input.durationMs()));
        }
        case "summary" -> {
          DistributionSummary.builder(input.name())
                  .tags(tags)
                  .register(registry)
                  .record(input.value());
        }
        default -> {
          // Unreachable: validate() already rejected unknown types.
          return Result.failure(new IllegalArgumentException(
                  "metric.type must be one of: counter, gauge, timer, summary"));
        }
      }
      return Result.success(new MetricOut(true));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static void validate(@NonNull MetricIn input) {
    if (input.name() == null || input.name().isBlank()) {
      throw new IllegalArgumentException("metric.name is required");
    }
    if (input.type() == null) {
      throw new IllegalArgumentException(
              "metric.type must be one of: counter, gauge, timer, summary");
    }
    String normalized = input.type().toLowerCase(Locale.ROOT);
    if (!TYPES.contains(normalized)) {
      throw new IllegalArgumentException(
              "metric.type must be one of: counter, gauge, timer, summary, was: " + input.type());
    }
    switch (normalized) {
      case "counter" -> {
        long amount = input.amount() == null ? 1L : input.amount();
        if (amount < 0L) {
          throw new IllegalArgumentException("metric.amount must be >= 0");
        }
      }
      case "gauge", "summary" -> {
        if (input.value() == null) {
          throw new IllegalArgumentException("metric.value is required");
        }
      }
      case "timer" -> {
        if (input.durationMs() == null) {
          throw new IllegalArgumentException("metric.durationMs is required");
        }
        if (input.durationMs() < 0L) {
          throw new IllegalArgumentException("metric.durationMs must be >= 0");
        }
      }
      default -> {
        // Unreachable: the membership check above already rejected unknown types.
      }
    }
  }

  private static @NonNull List<Tag> toTags(@NonNull Map<String, String> tags) {
    if (tags.isEmpty()) {
      return List.of();
    }
    List<Tag> result = new ArrayList<>(tags.size());
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      String key = entry.getKey();
      if (key == null) {
        throw new IllegalArgumentException("metric.tags key must not be null");
      }
      // Tag.of rejects null values; coerce null -> "" so the call succeeds.
      String value = entry.getValue() == null ? "" : entry.getValue();
      result.add(Tag.of(key, value));
    }
    return List.copyOf(result);
  }

  private @NonNull AtomicReference<Double> gaugeHolder(
          @NonNull String name, @NonNull Iterable<Tag> tags) {
    StringBuilder key = new StringBuilder(name);
    String sorted = StreamSupport.stream(tags.spliterator(), false)
            .map(t -> t.getKey() + "=" + t.getValue())
            .sorted()
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    if (!sorted.isEmpty()) {
      key.append('|').append(sorted);
    }
    return gaugeHolders.computeIfAbsent(key.toString(), k -> {
      AtomicReference<Double> holder = new AtomicReference<>(0.0d);
      Gauge.builder(name, holder, AtomicReference::get)
              .tags(tags)
              .register(registry);
      return holder;
    });
  }
}
