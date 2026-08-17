package cbs.nova.starter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class StarterApplication {

  public static void main(String[] args) {
    try {
      SpringApplication.run(StarterApplication.class, args);
    } catch (Exception e) {
      log.error("SYSTEM_ERROR: ", e);
      stopApp();
    }
  }

  private static void stopApp() {
    var thread = new Thread(() -> {
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException ignore) {}

      System.exit(1);
    });
    thread.setDaemon(true);
    thread.start();
  }
}
