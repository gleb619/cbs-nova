package cbs.nova.starter;

import cbs.nova.starter.core.StarterConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

@Slf4j
@SpringBootApplication
public class StarterApplication {

  public static void main(String[] args) {
    try {
      var application = new SpringApplication(StarterApplication.class);
      application.setApplicationStartup(new BufferingApplicationStartup(bufferCapacity()));
      application.run(args);
    } catch (Exception e) {
      log.error("SYSTEM_ERROR: ", e);
      stopApp();
    }
  }

  private static void stopApp() {
    var thread = new Thread(() -> {
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException ignore) {
      }

      System.exit(1);
    });
    thread.setDaemon(true);
    thread.start();
  }

  private static int bufferCapacity() {
    return bufferCapacity(System.getenv(StarterConstants.STARTUP_BUFFER_CAPACITY_ENV_KEY));
  }

  static int bufferCapacity(String envValue) {
    Integer propertyValue = Integer.getInteger(StarterConstants.STARTUP_BUFFER_CAPACITY_PROPERTY,
            null);
    if (propertyValue != null) {
      return propertyValue;
    }
    if (envValue != null && !envValue.isBlank()) {
      try {
        return Integer.parseInt(envValue.trim());
      } catch (NumberFormatException e) {
        log.warn("Malformed startup buffer capacity env value '{}': {}", envValue,
                StarterConstants.DEFAULT_STARTUP_BUFFER_CAPACITY);
      }
    }
    return StarterConstants.DEFAULT_STARTUP_BUFFER_CAPACITY;
  }
}
