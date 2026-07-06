package cbs.nova.starter.listeners;

import cbs.nova.starter.ExternalCallListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listener for tracking external API-related external calls. Logs external API operations and can
 * be extended to capture detailed metrics.
 */
@Component
public class ExternalApiCallListener implements ExternalCallListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExternalApiCallListener.class);

  @Override
  public void onCall(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload) {
    if (!type.equals("external_api")) {
      return; // Only handle external API calls
    }

    // Log external API call details
    LOGGER.info("EXTERNAL API CALL - Operation: {}, Target: {}, Payload: {}", operation, target,
            payload);

    // Additional processing can be added here, such as:
    // - Capturing API response times and status codes
    // - Tracking third-party service usage
    // - Monitoring for API rate limiting
    // - Recording external service dependencies
  }
}
