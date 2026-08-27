package cbs.nova.starter.helper.model;

import java.util.List;
import java.util.Map;

/**
 * Dedicated HTTP-call context entity. Normalises an {@link HttpCallIn} record into the runtime view
 * used by {@link cbs.nova.starter.helper.HttpCallHelper}.
 */
public record HttpCallContext(
        String url,
        String method,
        Map<String, String> headers,
        String body,
        long timeoutMillis,
        HttpCallIn.RedirectPolicy redirectPolicy,
        List<Integer> validStatuses) {

  public static HttpCallContext from(HttpCallIn input) {
    return new HttpCallContext(
            input.url(),
            input.effectiveMethod(),
            input.effectiveHeaders(),
            input.body(),
            input.effectiveTimeoutMillis(),
            input.effectiveRedirects(),
            input.effectiveValidStatuses());
  }

  public boolean isValidStatus(int status) {
    if (validStatuses.isEmpty()) {
      return status >= 200 && status < 300;
    }
    return validStatuses.contains(status);
  }
}
