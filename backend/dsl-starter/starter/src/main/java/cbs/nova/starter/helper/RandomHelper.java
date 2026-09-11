package cbs.nova.starter.helper;

import static cbs.nova.starter.core.StarterConstants.RANDOM_ALPHA;
import static cbs.nova.starter.core.StarterConstants.RANDOM_ALPHANUMERIC;
import static cbs.nova.starter.core.StarterConstants.RANDOM_BASE64URL;
import static cbs.nova.starter.core.StarterConstants.RANDOM_HEX;
import static cbs.nova.starter.core.StarterConstants.RANDOM_MAX_STRING_LENGTH;
import static cbs.nova.starter.core.StarterConstants.RANDOM_NUMERIC;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.model.RandomIn;
import cbs.nova.starter.helper.model.RandomOut;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.NonNull;

/**
 * Generates non-cryptographic pseudo-random values.
 *
 * <p>
 * Backed by {@link ThreadLocalRandom}, this helper is intended for sample data, ids, and load-test
 * jitter — NOT for secrets, tokens, or any security-sensitive use case. For cryptographic RNG use a
 * dedicated helper that wraps {@link java.security.SecureRandom}.
 *
 * <p>
 * Five modes:
 * <ul>
 * <li>{@code "int"} — inclusive integer in {@code [intMin, intMax]}.</li>
 * <li>{@code "long"} — inclusive long in {@code [longMin, longMax]}.</li>
 * <li>{@code "double"} — half-open double in {@code [doubleMin, doubleMax)} (defaults
 * {@code 0.0}/{@code 1.0}).</li>
 * <li>{@code "string"} — random string of {@code length} characters from a named pool
 * ({@code "alphanumeric"} (default), {@code "alpha"}, {@code "numeric"}, {@code "hex"},
 * {@code "base64url"}).</li>
 * <li>{@code "choice"} — uniformly random element of {@code list}.</li>
 * </ul>
 */
@Helper(name = "random")
public class RandomHelper implements Executable<RandomIn, RandomOut> {

  @Override
  public @NonNull Result<RandomOut> execute(@NonNull Context<RandomIn> ctx) {
    try {
      RandomIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "int" -> Result.success(new RandomOut(nextInt(input)));
        case "long" -> Result.success(new RandomOut(nextLong(input)));
        case "double" -> Result.success(new RandomOut(nextDouble(input)));
        case "string" -> Result.success(new RandomOut(nextString(input)));
        case "choice" -> Result.success(new RandomOut(choice(input)));
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "random.mode must be one of int, long, double, string, choice, was: "
                                + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static int nextInt(RandomIn input) {
    Integer min = input.intMin();
    Integer max = input.intMax();
    if (min == null || max == null) {
      throw new IllegalArgumentException("random.int requires intMin and intMax");
    }
    if (min > max) {
      throw new IllegalArgumentException(
              "random.int.min must be <= int.max, was: " + min + " > " + max);
    }
    if (min.intValue() == max.intValue()) {
      return min;
    }
    // Promote to long so max+1 cannot overflow; cast back to int for the result.
    long lo = min.longValue();
    long hi = max.longValue() + 1L;
    return (int) ThreadLocalRandom.current().nextLong(lo, hi);
  }

  private static long nextLong(RandomIn input) {
    Long min = input.longMin();
    Long max = input.longMax();
    if (min == null || max == null) {
      throw new IllegalArgumentException("random.long requires longMin and longMax");
    }
    if (min > max) {
      throw new IllegalArgumentException(
              "random.long.min must be <= long.max, was: " + min + " > " + max);
    }
    if (min.longValue() == max.longValue()) {
      return min;
    }
    // nextLong(origin, bound) excludes bound; bound = max+1 overflows at Long.MAX_VALUE, so use the
    // unbounded form (which returns Long.MIN_VALUE..Long.MAX_VALUE-1) and clip to [min, max].
    if (max == Long.MAX_VALUE) {
      return ThreadLocalRandom.current().nextLong(min, Long.MAX_VALUE);
    }
    return ThreadLocalRandom.current().nextLong(min, max + 1L);
  }

  private static double nextDouble(RandomIn input) {
    double min = input.doubleMin() == null ? 0.0 : input.doubleMin();
    double max = input.doubleMax() == null ? 1.0 : input.doubleMax();
    if (min > max) {
      throw new IllegalArgumentException(
              "random.double.min must be <= double.max, was: " + min + " > " + max);
    }
    if (min == max) {
      return min;
    }
    return ThreadLocalRandom.current().nextDouble(min, max);
  }

  private static String nextString(RandomIn input) {
    Integer length = input.length();
    if (length == null) {
      throw new IllegalArgumentException("random.string requires length");
    }
    if (length < 0) {
      throw new IllegalArgumentException("random.string.length must be >= 0, was: " + length);
    }
    if (length > RANDOM_MAX_STRING_LENGTH) {
      throw new IllegalArgumentException(
              "random.string.length must be <= " + RANDOM_MAX_STRING_LENGTH + ", was: " + length);
    }
    String charset = (input.charset() == null)
            ? "alphanumeric"
            : input.charset().toLowerCase(Locale.ROOT);
    String pool = switch (charset) {
      case "alphanumeric" -> RANDOM_ALPHANUMERIC;
      case "alpha" -> RANDOM_ALPHA;
      case "numeric" -> RANDOM_NUMERIC;
      case "hex" -> RANDOM_HEX;
      case "base64url" -> RANDOM_BASE64URL;
      default -> throw new IllegalArgumentException(
              "random.string.charset must be one of alphanumeric, alpha, numeric, hex, base64url, was: "
                      + input.charset());
    };
    if (length == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(length);
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < length; i++) {
      sb.append(pool.charAt(rnd.nextInt(pool.length())));
    }
    return sb.toString();
  }

  private static Object choice(RandomIn input) {
    List<Object> list = input.list();
    if (list == null) {
      throw new IllegalArgumentException("random.choice requires list");
    }
    if (list.isEmpty()) {
      throw new IllegalArgumentException("random.choice.list must not be empty");
    }
    return list.get(ThreadLocalRandom.current().nextInt(list.size()));
  }
}
