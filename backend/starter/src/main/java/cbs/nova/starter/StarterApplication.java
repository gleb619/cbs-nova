package cbs.nova.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//TODO: starter cant have a Application class, move it to integrationTest
@SpringBootApplication
public class StarterApplication {
  public static void main(String[] args) {
    SpringApplication.run(StarterApplication.class, args);
  }
}
