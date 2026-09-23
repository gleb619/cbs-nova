package cbs.nova.starter.helper.model;

import java.util.List;
import java.util.Map;

/**
 * Dedicated HTTP-call context entity. Normalises an {@link HttpCallIn} record into the runtime view
 * used by {@link cbs.nova.starter.helper.HttpCallHelper}.
 *
 * <p>
 * {@code maxAttempts} is resolved to the runtime-effective integer (null / {@code <= 1} → 1, see
 * {@link HttpCallIn#effectiveMaxAttempts()}); {@code retryBackoffMillis} is similarly resolved via
 * {@link HttpCallIn#effectiveRetryBackoffMillis()}, clamping to {@code [0, 30000]}.
 */
public record HttpCallContext(
        String url,
        String method,
        Map<String, String> headers,
        String body,
        long timeoutMillis,
        HttpCallIn.RedirectPolicy redirectPolicy,
        List<Integer> validStatuses,
        int maxAttempts,
        long retryBackoffMillis) {

  public static HttpCallContext from(HttpCallIn input) {
    return new HttpCallContext(
            input.url(),
            input.effectiveMethod(),
            input.effectiveHeaders(),
            input.body(),
            input.effectiveTimeoutMillis(),
            input.effectiveRedirects(),
            input.effectiveValidStatuses(),
            input.effectiveMaxAttempts(),
            input.effectiveRetryBackoffMillis());
  }

  public boolean isValidStatus(int status) {
    if (validStatuses.isEmpty()) {
      return status >= 200 && status < 300;
    }
    return validStatuses.contains(status);
  }

  /**
   * Mirrors {@link HttpCallHelper.HttpCallHelper#isRetryableStatus(int)}'s policy: 5xx plus 429 are
   * retryable; any other non-2xx status (including 408, 425) is not.
   */
  public boolean isRetryableStatus(int status) {
    return status == 429 || (status >= 500 && status < 600);
  }
}
