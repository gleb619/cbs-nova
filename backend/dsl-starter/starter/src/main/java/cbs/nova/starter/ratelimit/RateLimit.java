package cbs.nova.starter.ratelimit;

/**
 * Token-bucket parameters for a single rate-limit decision.
 */
public record RateLimit(int capacity, double refillPerSecond) {

  public RateLimit {
    if (capacity <= 0) {
      capacity = 20;
    }
    if (refillPerSecond <= 0) {
      refillPerSecond = 5.0;
    }
  }
}
