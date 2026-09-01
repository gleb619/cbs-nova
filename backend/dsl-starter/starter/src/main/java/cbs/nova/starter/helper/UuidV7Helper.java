package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.UuidV7In;
import cbs.nova.starter.helper.model.UuidV7Out;
import io.temporal.workflow.Workflow;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates RFC 9562 version-7 UUIDs.
 *
 * <p>
 * Each UUID starts with a 48-bit big-endian Unix timestamp in milliseconds, followed by the version
 * nibble {@code 7}, a 12-bit monotonic counter in {@code rand_a}, the RFC 4122 variant bits
 * {@code 10}, and 62 random trailing bits in {@code rand_b}. The resulting canonical, lower-case,
 * dashed string is lexicographically sortable and strictly increasing within a single millisecond.
 *
 * <p>
 * The optional {@code namespace} argument is mixed into the random tail rather than the
 * timestamp/counter portion: when a non-blank namespace is supplied, the 62 random trailing bits
 * are derived deterministically from the first 62 bits of {@code SHA-256(namespace)}. The timestamp
 * and monotonic counter still lead, so ordering is preserved and multiple calls with the same
 * namespace remain monotonic. Different namespaces produce different random tails.
 *
 * <p>
 * Thread-safety is provided by synchronizing the monotonic counter; uniqueness across threads is
 * guaranteed for unique timestamp/counter combinations.
 */
@Helper(name = "uuidV7")
public class UuidV7Helper implements Executable<UuidV7In, UuidV7Out> {

  private final Object lock = new Object();
  private long lastMs = -1L;
  private int counter;

  @Override
  public @NonNull Result<UuidV7Out> execute(@NonNull Context<UuidV7In> ctx) {
    try {
      UuidV7In input = ctx.body();
      TimestampCounter tc = nextTimestampCounter();
      byte[] tail = randomTail(input.namespace());
      String uuid = formatUuid(tc.ms, tc.counter, tail);
      return Result.success(new UuidV7Out(uuid));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private TimestampCounter nextTimestampCounter() {
    synchronized (lock) {
      long actual = now();
      if (actual > lastMs) {
        lastMs = actual;
        counter = 0;
      }
      int c = counter;
      if (c > 0xFFF) {
        lastMs++;
        counter = 0;
        c = 0;
      }
      counter = c + 1;
      if (counter > 0xFFF) {
        lastMs++;
        counter = 0;
      }
      return new TimestampCounter(lastMs, c);
    }
  }

  private static long now() {
    try {
      return Workflow.currentTimeMillis();
    } catch (Throwable e) {
      return System.currentTimeMillis();
    }
  }

  private static byte[] randomTail(@Nullable String namespace) {
    byte[] tail = new byte[8];
    if (namespace != null && !namespace.isBlank()) {
      byte[] digest;
      try {
        digest = MessageDigest.getInstance("SHA-256")
                .digest(namespace.getBytes(StandardCharsets.UTF_8));
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 is not available", e);
      }
      System.arraycopy(digest, 0, tail, 0, Math.min(digest.length, tail.length));
    } else {
      ThreadLocalRandom.current().nextBytes(tail);
    }
    tail[0] = (byte) ((tail[0] & 0x3F) | 0x80);
    return tail;
  }

  private static String formatUuid(long ms, int counter, byte[] tail) {
    long msb = (ms << 16) | (0x7000L | (counter & 0x0FFFL));
    long lsb = 0L;
    for (byte b : tail) {
      lsb = (lsb << 8) | (b & 0xFFL);
    }
    return String.format(Locale.ROOT,
            "%08x-%04x-%04x-%04x-%012x",
            (msb >>> 32) & 0xFFFFFFFFL,
            (msb >>> 16) & 0xFFFFL,
            msb & 0xFFFFL,
            (lsb >>> 48) & 0xFFFFL,
            lsb & 0xFFFFFFFFFFFFL);
  }

  private record TimestampCounter(long ms, int counter) {
  }
}
