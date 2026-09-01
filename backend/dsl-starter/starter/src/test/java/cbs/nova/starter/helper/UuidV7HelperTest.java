package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.UuidV7In;
import cbs.nova.starter.helper.model.UuidV7Out;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class UuidV7HelperTest {

  private static final Pattern UUID_V7 = Pattern.compile(
          "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

  private final ContextFactory contextFactory = new ContextFactory();
  private final UuidV7Helper helper = new UuidV7Helper();

  @Test
  void producesValidV7Format() {
    for (int i = 0; i < 100; i++) {
      var ctx = contextFactory.of(new UuidV7In(null), ExecutionMode.PREVIEW);
      Result<UuidV7Out> result = helper.execute(ctx);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value().uuid()).matches(UUID_V7);
    }
  }

  @Test
  void sequentialCallsAreStrictlyIncreasing() {
    String previous = null;
    for (int i = 0; i < 10_000; i++) {
      var ctx = contextFactory.of(new UuidV7In(null), ExecutionMode.PREVIEW);
      UuidV7Out out = helper.execute(ctx).value();
      if (previous != null) {
        assertThat(out.uuid()).isGreaterThan(previous);
      }
      previous = out.uuid();
    }
  }

  /**
   * When a namespace is supplied, the 62 random trailing bits are derived from
   * {@code SHA-256(namespace)}. In the canonical UUID string that makes the final 12-character
   * (node) group deterministic for the same namespace, while the timestamp and monotonic counter
   * keep the full value ordered. Different namespaces produce different node groups.
   */
  @Test
  void namespaceMakesTrailingGroupDeterministic() {
    String namespace = "payments/v1";
    String expectedTail = null;
    for (int i = 0; i < 10; i++) {
      var ctx = contextFactory.of(new UuidV7In(namespace), ExecutionMode.PREVIEW);
      String uuid = helper.execute(ctx).value().uuid();
      assertThat(uuid).matches(UUID_V7);
      String tail = uuid.substring(uuid.lastIndexOf('-') + 1);
      if (expectedTail == null) {
        expectedTail = tail;
      } else {
        assertThat(tail).isEqualTo(expectedTail);
      }
    }

    var otherCtx = contextFactory.of(new UuidV7In("orders/v1"), ExecutionMode.PREVIEW);
    String otherTail = helper.execute(otherCtx).value().uuid();
    otherTail = otherTail.substring(otherTail.lastIndexOf('-') + 1);
    assertThat(otherTail).isNotEqualTo(expectedTail);
  }

  @Test
  void concurrentCallsAreUniqueAndValid() throws Exception {
    int threads = 8;
    int callsPerThread = 500;
    Set<String> seen = ConcurrentHashMap.newKeySet();
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        futures.add(executor.submit(() -> {
          for (int i = 0; i < callsPerThread; i++) {
            var ctx = contextFactory.of(new UuidV7In(null), ExecutionMode.PREVIEW);
            String uuid = helper.execute(ctx).value().uuid();
            if (!uuid.matches(UUID_V7.pattern())) {
              throw new AssertionError("Invalid UUID: " + uuid);
            }
            if (!seen.add(uuid)) {
              throw new AssertionError("Duplicate UUID: " + uuid);
            }
          }
        }));
      }
      for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
    assertThat(seen).hasSize(threads * callsPerThread);
  }
}
