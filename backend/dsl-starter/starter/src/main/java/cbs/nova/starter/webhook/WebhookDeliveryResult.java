package cbs.nova.starter.webhook;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of a single webhook delivery attempt. {@code status} is an HTTP status code as a string
 * on a completed response, or one of {@code serialization_failed}, {@code rejected},
 * {@code failed}, {@code interrupted}. Returned by
 * {@link WebhookDispatcher#dispatchTo(String, String, String, Object)} for rule-based deliveries
 * (T565) which audit through {@code dsl_notification_rule_firing} instead of the static delivery
 * log.
 */
public record WebhookDeliveryResult(
        String status,
        int attempts,
        @Nullable String error,
        long durationMs) {

  /** True when {@code status} is a 2xx/3xx HTTP status code. */
  public boolean isSuccess() {
    try {
      int code = Integer.parseInt(status);
      return code >= 200 && code < 400;
    } catch (NumberFormatException ex) {
      return false;
    }
  }
}
