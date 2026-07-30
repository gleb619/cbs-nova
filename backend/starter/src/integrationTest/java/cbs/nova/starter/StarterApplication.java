package cbs.nova.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap for integration tests. Lives in the {@code integrationTest} source set so the published
 * starter library ships no {@code @SpringBootApplication} entry point.
 */
@SpringBootApplication
public class StarterApplication {
  public static void main(String[] args) {
    SpringApplication.run(StarterApplication.class, args);
  }
}
