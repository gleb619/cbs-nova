package cbs.nova.starter.listeners;

import cbs.nova.starter.ExternalCallListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listener for tracking database-related external calls.
 * Logs database operations and can be extended to capture detailed metrics.
 */
@Component
public class DatabaseCallListener implements ExternalCallListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseCallListener.class);

  @Override
  public void onCall(@NonNull String type, @NonNull String target, @NonNull String operation, @Nullable Object payload) {
    if (!type.equals("database")) {
      return; // Only handle database calls
    }

    // Log database call details
    LOGGER.info("DB CALL - Operation: {}, Target: {}, Payload: {}", operation, target, payload);

    // Additional processing can be added here, such as:
    // - Capturing query performance metrics
    // - Tracking connection pool usage
    // - Monitoring for slow queries
    // - Recording transaction boundaries
  }
}