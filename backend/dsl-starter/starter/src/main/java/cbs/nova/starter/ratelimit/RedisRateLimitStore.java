package cbs.nova.starter.ratelimit;

import java.util.Collections;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis-backed token-bucket store. Buckets are shared across JVM instances and survive restarts of
 * any single node. Keys are scoped to a configurable prefix and expire after 24 hours of
 * inactivity.
 */
public final class RedisRateLimitStore implements RateLimitStore {

  private static final String KEY_PREFIX = "cbs:ratelimit:";
  private static final int TTL_SECONDS = 86_400;
  private static final String REQUESTED_TOKENS = "1";

  /**
   * Atomically refills and consumes one token from a bucket. Stores fractional tokens as strings so
   * high refill rates work correctly, but returns only the allowed flag and retry-after seconds to
   * avoid Redis coercing doubles to integers on the Lua return path.
   */
  private static final String TOKEN_BUCKET_LUA = """
          local key = KEYS[1]
          local capacity = tonumber(ARGV[1])
          local refillPerSecond = tonumber(ARGV[2])
          local nowMs = tonumber(ARGV[3])
          local requested = tonumber(ARGV[4])

          local bucket = redis.call('HMGET', key, 'tokens', 'lastRefillMs')
          local tokens = capacity
          local lastRefillMs = nowMs
          if bucket[1] ~= false then
            tokens = tonumber(bucket[1])
            lastRefillMs = tonumber(bucket[2])
          end

          local elapsedSeconds = (nowMs - lastRefillMs) / 1000.0
          local newTokens = math.min(capacity, tokens + elapsedSeconds * refillPerSecond)
          local allowed = 0
          local retryAfter = 0
          if newTokens >= requested then
            newTokens = newTokens - requested
            allowed = 1
          else
            retryAfter = math.ceil((requested - newTokens) / refillPerSecond)
            if retryAfter < 1 then
              retryAfter = 1
            end
          end

          redis.call('HMSET', key, 'tokens', tostring(newTokens), 'lastRefillMs', tostring(nowMs))
          redis.call('EXPIRE', key, %d)
          return {allowed, retryAfter}
          """.formatted(TTL_SECONDS);

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<List> script;

  public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    this.script = RedisScript.of(TOKEN_BUCKET_LUA, List.class);
  }

  @Override
  public Consumption consume(String key, RateLimit rateLimit) {
    String redisKey = KEY_PREFIX + key;
    long nowMs = System.currentTimeMillis();
    List<Object> result = redisTemplate.execute(
            script,
            Collections.singletonList(redisKey),
            String.valueOf(rateLimit.capacity()),
            String.valueOf(rateLimit.refillPerSecond()),
            String.valueOf(nowMs),
            REQUESTED_TOKENS);
    if (result == null || result.isEmpty()) {
      return new Consumption(false, 1L);
    }
    boolean allowed = Long.valueOf(1L).equals(asLong(result.get(0)));
    long retryAfterSeconds = result.size() > 1 ? asLong(result.get(1)) : 0L;
    return new Consumption(allowed, allowed ? 0L : Math.max(1L, retryAfterSeconds));
  }

  private static long asLong(Object value) {
    if (value instanceof Number n) {
      return n.longValue();
    }
    if (value instanceof String s) {
      try {
        return Long.parseLong(s);
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    return 0L;
  }
}
