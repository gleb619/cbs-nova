package cbs.nova.starter.listeners;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpCallListener implements ExternalCallListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpCallListener.class);

  @Override
  public void onCall(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload) {
    if (!type.equals("http")) {
      return;
    }

    LOGGER.info("HTTP CALL - Operation: {}, Target: {}, Payload: {}", operation, target, payload);
  }
}
