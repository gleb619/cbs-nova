package cbs.nova.starter.listeners;

import cbs.nova.starter.ExternalCallListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listener for tracking file system-related external calls.
 * Logs file operations and can be extended to capture detailed metrics.
 */
@Component
public class FileSystemCallListener implements ExternalCallListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemCallListener.class);

  @Override
  public void onCall(@NonNull String type, @NonNull String target, @NonNull String operation, @Nullable Object payload) {
    if (!type.equals("filesystem")) {
      return; // Only handle file system calls
    }

    // Log file system call details
    LOGGER.info("FS CALL - Operation: {}, Target: {}, Payload: {}", operation, target, payload);

    // Additional processing can be added here, such as:
    // - Tracking file access patterns
    // - Monitoring file sizes and types
    // - Tracking read/write performance
    // - Recording directory access
  }
}