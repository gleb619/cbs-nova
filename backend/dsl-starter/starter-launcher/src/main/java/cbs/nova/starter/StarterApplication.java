package cbs.nova.starter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

@Slf4j
@SpringBootApplication
public class StarterApplication {

  private static final int DEFAULT_STARTUP_BUFFER_CAPACITY = 10_000;

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
    // TODO: add ENV support, move magic code to a `cbs.nova.starter.core.StarterConstants`
    return Integer.getInteger("cbs.startup.buffer.capacity", DEFAULT_STARTUP_BUFFER_CAPACITY);
  }
}
