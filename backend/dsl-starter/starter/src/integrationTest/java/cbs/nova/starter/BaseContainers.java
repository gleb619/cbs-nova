package cbs.nova.starter;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.function.Supplier;

@Testcontainers
public abstract class BaseContainers {

  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
  private static final int START_ATTEMPTS = 3;

  private static GenericContainer<?> newTemporalContainer() {
    return new GenericContainer<>(
            DockerImageName.parse("temporalio/auto-setup:1.25.2"))
            .withExposedPorts(7233)
            .withEnv("DB", "sqlite")
            .withEnv("BIND_ON_IP", "0.0.0.0")
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("temporal"))
            .withCommand(
                    "server",
                    "start-dev",
                    "--ip", "0.0.0.0",
                    "--namespace", "default",
                    "--db-filename", "/tmp/temporal.db")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(STARTUP_TIMEOUT));
  }

  private static GenericContainer<?> newKeycloakContainer() {
    return new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:22.0.0"))
            .withExposedPorts(8080)
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HOSTNAME", "localhost")
            .withEnv("KC_HOSTNAME_STRICT", "false")
            .withEnv("KC_HOSTNAME_STRICT_HTTPS", "false")
            .withCommand("start-dev")
            .waitingFor(Wait.forHttp("/realms/master").forPort(8080)
                    .withStartupTimeout(STARTUP_TIMEOUT));
  }

  /**
   * Start a container with retry on port-allocation races across parallel JVM forks. Each attempt
   * builds a fresh container so testcontainers re-rolls a random host port.
   */
  private static <T extends GenericContainer<?>> T startWithRetry(String name,
          Supplier<T> factory) {
    ContainerLaunchException last = null;
    for (int i = 1; i <= START_ATTEMPTS; i++) {
      T container = factory.get();
      try {
        container.start();
        return container;
      } catch (ContainerLaunchException e) {
        last = e;
        try {
          container.stop();
        } catch (Exception ignored) {
        }
      }
    }
    throw new IllegalStateException("Failed to start " + name
            + " after " + START_ATTEMPTS + " attempts", last);
  }

  protected static final GenericContainer<?> TEMPORAL = startWithRetry("temporal",
          BaseContainers::newTemporalContainer);

  protected static final GenericContainer<?> KEYCLOAK = startWithRetry("keycloak",
          BaseContainers::newKeycloakContainer);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("temporal.target",
            () -> "%s:%d".formatted(TEMPORAL.getHost(), TEMPORAL.getMappedPort(7233)));
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> "http://%s:%d/realms/cbs-nova".formatted(KEYCLOAK.getHost(),
                    KEYCLOAK.getMappedPort(8080)));
    registry.add("testcontainers.temporal.mapped-port",
            () -> Integer.toString(TEMPORAL.getMappedPort(7233)));
  }
}
