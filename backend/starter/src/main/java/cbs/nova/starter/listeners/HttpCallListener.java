package cbs.nova.starter.listeners;

import cbs.nova.starter.ExternalCallListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listener for tracking HTTP-related external calls.
 * Logs HTTP operations and can be extended to capture detailed metrics.
 */
@Component
public class HttpCallListener implements ExternalCallListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpCallListener.class);

  @Override
  public void onCall(@NonNull String type, @NonNull String target, @NonNull String operation, @Nullable Object payload) {
    if (!type.equals("http")) {
      return; // Only handle HTTP calls
    }

    // Log HTTP call details
    LOGGER.info("HTTP CALL - Operation: {}, Target: {}, Payload: {}", operation, target, payload);

    // Additional processing can be added here, such as:
    // - Capturing response times and status codes
    // - Tracking API endpoint usage
    // - Monitoring for failed requests
    // - Recording request/response sizes
  }
}