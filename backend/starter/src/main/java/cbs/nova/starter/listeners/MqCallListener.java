package cbs.nova.starter.listeners;

import cbs.nova.starter.ExternalCallListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listener for tracking message queue-related external calls. Logs MQ operations and can be
 * extended to capture detailed metrics.
 */
@Component
public class MqCallListener implements ExternalCallListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(MqCallListener.class);

  @Override
  public void onCall(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload) {
    if (!type.equals("mq")) {
      return; // Only handle MQ calls
    }

    // Log MQ call details
    LOGGER.info("MQ CALL - Operation: {}, Target: {}, Payload: {}", operation, target, payload);

    // Additional processing can be added here, such as:
    // - Tracking message throughput
    // - Monitoring queue depths
    // - Tracking message acknowledgment rates
    // - Recording message sizes
  }
}
