package cbs.nova.starter.listeners;

import cbs.nova.starter.ExternalCallListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listener for tracking microservice-related external calls. Logs microservice operations and can
 * be extended to capture detailed metrics.
 */
@Component
public class MicroserviceCallListener implements ExternalCallListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(MicroserviceCallListener.class);

  @Override
  public void onCall(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload) {
    if (!type.equals("microservice")) {
      return; // Only handle microservice calls
    }

    // Log microservice call details
    LOGGER.info("MICROSERVICE CALL - Operation: {}, Target: {}, Payload: {}", operation, target,
            payload);

    // Additional processing can be added here, such as:
    // - Tracking service-to-service latency
    // - Monitoring circuit breaker states
    // - Tracking service dependency graphs
    // - Recording payload sizes and frequencies
  }
}
