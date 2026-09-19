package cbs.nova.starter.ratelimit;

/**
 * Backend for rate-limit token buckets. Implementations must be thread-safe.
 */
public interface RateLimitStore {

  /**
   * Attempts to consume one token from the bucket identified by {@code key}. The key already
   * encodes both the principal and the route class, so implementations do not need to parse it.
   *
   * @param key
   *          the bucket key (principal + route class)
   * @param rateLimit
   *          the token-bucket parameters to apply
   * @return whether the request is allowed and, if not, how many seconds to wait
   */
  Consumption consume(String key, RateLimit rateLimit);
}
